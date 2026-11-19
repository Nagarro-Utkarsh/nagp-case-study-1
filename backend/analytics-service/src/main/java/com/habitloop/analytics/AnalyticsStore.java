package com.habitloop.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Component
class AnalyticsStore {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsStore.class);
    private final DynamoDbClient dynamo;
    private final String table;
    private final StreakCalculator streakCalculator = new StreakCalculator();

    AnalyticsStore(DynamoDbClient dynamo, @Value("${ANALYTICS_TABLE:habitloop-analytics}") String table) {
        this.dynamo = dynamo; this.table = table;
    }

    @PostConstruct
    void ensureTable() {
        try {
            dynamo.describeTable(DescribeTableRequest.builder().tableName(table).build());
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException missing) {
            try {
                dynamo.createTable(CreateTableRequest.builder().tableName(table)
                        .keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                                KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                        .attributeDefinitions(
                                software.amazon.awssdk.services.dynamodb.model.AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                                software.amazon.awssdk.services.dynamodb.model.AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                        .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build()).build());
            } catch (software.amazon.awssdk.services.dynamodb.model.ResourceInUseException concurrentCreate) {
                log.info("Analytics table {} is being created by another task", table);
            }
        }
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                if ("ACTIVE".equals(dynamo.describeTable(DescribeTableRequest.builder().tableName(table).build()).table().tableStatusAsString())) return;
            } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException notReady) {
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for DynamoDB table creation", ex);
            }
        }
        throw new IllegalStateException("DynamoDB table did not become active: " + table);
    }

    void registerSignup(UUID tenantId, UUID userId, Instant signedUpAt) {
        String pk = tenantId.toString();
        String sk = "USER#" + userId;
        Map<String, AttributeValue> existing = get(pk, sk);
        String stored = string(existing, "signedUpAt");
        String supplied = signedUpAt.toString();
        if (stored != null) {
            if (!stored.equals(supplied)) {
                log.warn("Conflicting signup timestamp for tenant {} user {}", tenantId, userId);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "User was already registered with a different signup time");
            }
            return;
        }
        LocalDate date = signedUpAt.atZone(ZoneOffset.UTC).toLocalDate();
        WeekFields iso = WeekFields.ISO;
        String week = "%04d-W%02d".formatted(date.get(iso.weekBasedYear()), date.get(iso.weekOfWeekBasedYear()));
        try {
            dynamo.updateItem(UpdateItemRequest.builder().tableName(table).key(key(pk, sk))
                    .updateExpression("SET #signup=:signup, #week=:week, #logs=if_not_exists(#logs,:zero)")
                    .conditionExpression("attribute_not_exists(#signup)")
                    .expressionAttributeNames(Map.of("#signup", "signedUpAt", "#week", "signupWeek", "#logs", "totalLogs"))
                    .expressionAttributeValues(Map.of(":signup", s(supplied), ":week", s(week), ":zero", n(0))).build());
        } catch (software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException conflict) {
            String actual = string(get(pk, sk), "signedUpAt");
            if (!supplied.equals(actual)) {
                log.warn("Conflicting signup timestamp for tenant {} user {}", tenantId, userId);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "User was already registered with a different signup time");
            }
        }
    }

    boolean applyActivity(UUID logId, UUID tenantId, UUID userId, UUID habitId, Instant loggedAt) {
        String userPk = tenantId + "#" + userId;
        String userSummaryPk = tenantId.toString();
        String userSk = "USER#" + userId;
        String day = loggedAt.atZone(ZoneOffset.UTC).toLocalDate().toString();
        String daySk = "DAY#" + habitId + "#" + day;
        String streakSk = "STREAK#" + habitId;
        String logSk = "LOG#" + logId;
        Map<String, AttributeValue> summary = get(userSummaryPk, userSk);
        String observedActivity = string(summary, "lastActiveAt");
        Instant latestActivity = observedActivity == null ? loggedAt : later(Instant.parse(observedActivity), loggedAt);
        String latestDate = latestActivity.atZone(ZoneOffset.UTC).toLocalDate().toString();
        Map<String, AttributeValue> streak = get(userPk, streakSk);
        long expectedVersion = number(streak, "version", -1);
        Set<LocalDate> activeDates = allHabitDays(userPk, habitId);
        activeDates.add(loggedAt.atZone(ZoneOffset.UTC).toLocalDate());
        StreakCalculator.Result computed = streakCalculator.calculate(activeDates, LocalDate.now(ZoneOffset.UTC));
        long nextVersion = expectedVersion + 1;
        Instant updatedAt = Instant.now();
        String userCondition = observedActivity == null ? "attribute_not_exists(#active)" : "#active=:observed";
        Map<String, String> userNames = Map.of("#active", "lastActiveAt", "#date", "lastActiveDate",
                "#updated", "lastUpdatedAt", "#logs", "totalLogs");
        Map<String, AttributeValue> userValues = new HashMap<>();
        userValues.put(":active", s(latestActivity.toString())); userValues.put(":date", s(latestDate));
        userValues.put(":updated", s(updatedAt.toString())); userValues.put(":one", n(1));
        if (observedActivity != null) userValues.put(":observed", s(observedActivity));

        Update.Builder streakUpdate = Update.builder().tableName(table).key(key(userPk, streakSk))
                .updateExpression("SET #current=:current, #longest=:longest, #last=:last, #version=:next")
                .expressionAttributeNames(Map.of("#current", "current", "#longest", "longest", "#last", "lastDate", "#version", "version"))
                .expressionAttributeValues(Map.of(":current", n(computed.current()), ":longest", n(computed.longest()),
                        ":last", s(computed.lastDate() == null ? "" : computed.lastDate().toString()), ":next", n(nextVersion)));
        if (expectedVersion < 0) streakUpdate.conditionExpression("attribute_not_exists(#version)");
        else streakUpdate.conditionExpression("#version=:expected").expressionAttributeValues(Map.of(":current", n(computed.current()),
                ":longest", n(computed.longest()), ":last", s(computed.lastDate() == null ? "" : computed.lastDate().toString()),
                ":next", n(nextVersion), ":expected", n(expectedVersion)));

        List<TransactWriteItem> actions = List.of(
                TransactWriteItem.builder().put(Put.builder().tableName(table).item(key(userPk, logSk))
                        .conditionExpression("attribute_not_exists(pk) AND attribute_not_exists(sk)").build()).build(),
                TransactWriteItem.builder().update(Update.builder().tableName(table).key(key(userPk, daySk))
                        .updateExpression("SET #hours=list_append(if_not_exists(#hours,:empty),:hour) ADD #count :one")
                        .expressionAttributeNames(Map.of("#hours", "hours", "#count", "count"))
                        .expressionAttributeValues(Map.of(":empty", AttributeValue.builder().l(List.of()).build(),
                                ":hour", AttributeValue.builder().l(List.of(n(loggedAt.atZone(ZoneOffset.UTC).getHour()))).build(),
                                ":one", n(1))).build()).build(),
                TransactWriteItem.builder().update(Update.builder().tableName(table).key(key(userSummaryPk, userSk))
                        .updateExpression("SET #active=:active, #date=:date, #updated=:updated ADD #logs :one")
                        .conditionExpression(userCondition).expressionAttributeNames(userNames)
                        .expressionAttributeValues(userValues).build()).build(),
                TransactWriteItem.builder().update(streakUpdate.build()).build());
        try {
            dynamo.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(actions).build());
            return true;
        } catch (TransactionCanceledException conflict) {
            if (!get(userPk, logSk).isEmpty()) return false;
            log.warn("Analytics transaction conflict for log {} user {} habit {}", logId, userId, habitId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Analytics update conflicted; retry the activity");
        } catch (SdkException failure) {
            log.error("Analytics transaction failed for log {} user {} habit {}", logId, userId, habitId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Analytics update failed");
        }
    }

    Map<String, Object> trend(UUID tenantId, UUID userId, UUID habitId, int days) {
        Map<LocalDate, Integer> counts = dayCounts(tenantId, userId, habitId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            results.add(Map.of("date", date.toString(), "count", counts.getOrDefault(date, 0)));
        }
        return Map.of("habitId", habitId, "days", results);
    }

    int count(UUID tenantId, UUID userId, UUID habitId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must not precede from");
        return (int) dayCounts(tenantId, userId, habitId).entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(from) && !entry.getKey().isAfter(to))
                .mapToLong(Map.Entry::getValue).sum();
    }

    Map<String, Object> hours(UUID tenantId, UUID userId, UUID habitId, int days) {
        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(days - 1);
        Map<Integer, Integer> histogram = new TreeMap<>();
        for (var entry : dayItems(tenantId + "#" + userId, habitId)) {
            LocalDate date = dateFromDayKey(entry);
            if (date == null || date.isBefore(from) || date.isAfter(LocalDate.now(ZoneOffset.UTC))) continue;
            for (AttributeValue hour : entry.getOrDefault("hours", AttributeValue.builder().l(List.of()).build()).l()) {
                histogram.merge(Integer.parseInt(hour.n()), 1, Integer::sum);
            }
        }
        return Map.of("habitId", habitId, "hours", histogram);
    }

    List<Map<String, Object>> dashboard(UUID tenantId, UUID userId, List<HabitView> habits) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (HabitView habit : habits) {
            Map<LocalDate, Integer> counts = dayCounts(tenantId, userId, habit.id());
            int done = counts.getOrDefault(today, 0);
            int completedDays = 0;
            for (int i = 0; i < 7; i++) if (counts.getOrDefault(today.minusDays(i), 0) > 0) completedDays++;
            int targetDays = "WEEKLY".equalsIgnoreCase(habit.frequency())
                    ? Math.max(1, habit.daysOfWeek() == null ? 1 : habit.daysOfWeek().size()) : 7;
            Map<String, AttributeValue> streak = get(tenantId + "#" + userId, "STREAK#" + habit.id());
            result.add(Map.of("habitId", habit.id(), "name", habit.name(), "category", habit.category(),
                    "todayCount", done, "doneToday", done > 0, "currentStreak", number(streak, "current", 0),
                    "completionPercentage", Math.round(Math.min(100.0, completedDays * 100.0 / targetDays) * 100.0) / 100.0));
        }
        return result;
    }

    List<Map<String, Object>> cohorts(UUID tenantId) {
        Map<String, Integer> totals = new TreeMap<>();
        Map<String, Integer> active = new TreeMap<>();
        Instant threshold = Instant.now().minusSeconds(7 * 24 * 60 * 60);
        Map<String, AttributeValue> lastKey = null;
        do {
            QueryRequest.Builder query = QueryRequest.builder().tableName(table).consistentRead(true)
                    .keyConditionExpression("#pk=:pk AND begins_with(#sk,:prefix)")
                    .expressionAttributeNames(Map.of("#pk", "pk", "#sk", "sk"))
                    .expressionAttributeValues(Map.of(":pk", s(tenantId.toString()), ":prefix", s("USER#")));
            if (lastKey != null) query.exclusiveStartKey(lastKey);
            var page = dynamo.query(query.build());
            for (Map<String, AttributeValue> item : page.items()) {
                String week = string(item, "signupWeek");
                if (week == null) continue;
                totals.merge(week, 1, Integer::sum);
                String lastActive = string(item, "lastActiveAt");
                if (lastActive != null) {
                    Instant instant = Instant.parse(lastActive);
                    if (!instant.isBefore(threshold) && !instant.isAfter(Instant.now())) active.merge(week, 1, Integer::sum);
                }
            }
            lastKey = page.lastEvaluatedKey();
        } while (lastKey != null && !lastKey.isEmpty());
        return totals.keySet().stream().map(week -> {
            int total = totals.get(week), activeUsers = active.getOrDefault(week, 0);
            return Map.<String, Object>of("signupWeek", week, "totalUsers", total, "activeUsers", activeUsers,
                    "activePercentage", Math.round(activeUsers * 10000.0 / total) / 100.0);
        }).toList();
    }

    String lastUpdatedAt(UUID tenantId, UUID userId) { return string(get(tenantId.toString(), "USER#" + userId), "lastUpdatedAt"); }

    private Map<LocalDate, Integer> dayCounts(UUID tenant, UUID user, UUID habit) {
        Map<LocalDate, Integer> result = new HashMap<>();
        for (Map<String, AttributeValue> item : dayItems(tenant + "#" + user, habit)) {
            LocalDate date = dateFromDayKey(item);
            if (date != null) result.put(date, (int) number(item, "count", 0));
        }
        return result;
    }
    private List<Map<String, AttributeValue>> dayItems(String pk, UUID habitId) {
        List<Map<String, AttributeValue>> result = new ArrayList<>();
        Map<String, AttributeValue> lastKey = null;
        do {
            QueryRequest.Builder query = QueryRequest.builder().tableName(table).consistentRead(true)
                    .keyConditionExpression("#pk=:pk AND begins_with(#sk,:prefix)")
                    .expressionAttributeNames(Map.of("#pk", "pk", "#sk", "sk"))
                    .expressionAttributeValues(Map.of(":pk", s(pk), ":prefix", s("DAY#" + habitId + "#")));
            if (lastKey != null) query.exclusiveStartKey(lastKey);
            var page = dynamo.query(query.build());
            result.addAll(page.items()); lastKey = page.lastEvaluatedKey();
        } while (lastKey != null && !lastKey.isEmpty());
        return result;
    }
    private Set<LocalDate> allHabitDays(String pk, UUID habitId) {
        Set<LocalDate> dates = new HashSet<>();
        for (var item : dayItems(pk, habitId)) { LocalDate date = dateFromDayKey(item); if (date != null) dates.add(date); }
        return dates;
    }
    private LocalDate dateFromDayKey(Map<String, AttributeValue> item) {
        String sk = string(item, "sk");
        if (sk == null || sk.length() < 10) return null;
        try { return LocalDate.parse(sk.substring(sk.length() - 10)); } catch (RuntimeException ignored) { return null; }
    }
    private Instant later(Instant first, Instant second) { return first.isAfter(second) ? first : second; }
    private Map<String, AttributeValue> get(String pk, String sk) {
        Map<String, AttributeValue> result = dynamo.getItem(r -> r.tableName(table).key(key(pk, sk)).consistentRead(true)).item();
        return result == null ? Map.of() : result;
    }
    private Map<String, AttributeValue> key(String pk, String sk) { return Map.of("pk", s(pk), "sk", s(sk)); }
    private AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }
    private String string(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name); return value == null ? null : value.s();
    }
    private long number(Map<String, AttributeValue> item, String name, long fallback) {
        AttributeValue value = item.get(name); return value == null || value.n() == null ? fallback : Long.parseLong(value.n());
    }
    record HabitView(UUID id, String name, String category, String frequency, List<Integer> daysOfWeek) {}
}
