package com.habitloop.activity;

import com.habitloop.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/habits")
public class HabitController {
    private static final Logger log = LoggerFactory.getLogger(HabitController.class);
    private final HabitRepository habits;
    private final ActivityLogRepository logs;
    private final RestClient restClient;
    private final String analyticsUrl;
    private final String internalKey;

    HabitController(HabitRepository habits, ActivityLogRepository logs, RestClient.Builder builder,
                    @Value("${ANALYTICS_URL:http://localhost:8085}") String analyticsUrl,
                    @Value("${INTERNAL_KEY:local-internal-key}") String internalKey) {
        this.habits = habits; this.logs = logs; this.restClient = builder.build();
        this.analyticsUrl = analyticsUrl; this.internalKey = internalKey;
    }

    @GetMapping
    List<HabitView> list() {
        CurrentUser user = CurrentUser.get();
        return habits.findByTenantIdAndUserIdOrderByCreatedAtDesc(user.tenantId(), user.userId()).stream().map(this::view).toList();
    }

    @PostMapping
    HabitView create(@Valid @RequestBody HabitRequest request) {
        CurrentUser user = CurrentUser.get();
        validateSchedule(request.frequency(), request.daysOfWeek());
        Habit habit = new Habit();
        habit.id = UUID.randomUUID(); habit.tenantId = user.tenantId(); habit.userId = user.userId();
        habit.name = request.name().trim(); habit.category = request.category().trim(); habit.frequency = request.frequency().toUpperCase(java.util.Locale.ROOT);
        habit.daysOfWeek = days(request.daysOfWeek()); habit.preferredTime = request.preferredTime(); habit.createdAt = Instant.now();
        return view(habits.save(habit));
    }

    @GetMapping("/{id}")
    HabitView get(@PathVariable UUID id) { return view(owned(id)); }

    @PutMapping("/{id}")
    HabitView update(@PathVariable UUID id, @Valid @RequestBody HabitRequest request) {
        Habit habit = owned(id);
        validateSchedule(request.frequency(), request.daysOfWeek());
        habit.name = request.name().trim(); habit.category = request.category().trim();
        habit.frequency = request.frequency().toUpperCase(java.util.Locale.ROOT); habit.daysOfWeek = days(request.daysOfWeek());
        habit.preferredTime = request.preferredTime();
        return view(habits.save(habit));
    }

    @DeleteMapping("/{id}")
    void delete(@PathVariable UUID id) {
        Habit habit = owned(id);
        habits.delete(habit);
    }

    @PostMapping("/{id}/logs")
    LogView log(@PathVariable UUID id, @RequestBody(required = false) ManualLogRequest request) {
        CurrentUser user = CurrentUser.get();
        owned(id);
        ActivityLog log = new ActivityLog();
        log.id = UUID.randomUUID(); log.tenantId = user.tenantId(); log.userId = user.userId(); log.habitId = id;
        log.loggedAt = request == null || request.loggedAt() == null ? Instant.now() : request.loggedAt();
        log.source = "MANUAL"; log.analyticsSynced = false;
        ActivityLog saved = logs.saveAndFlush(log);
        sync(saved);
        return logView(saved);
    }

    @GetMapping("/{id}/logs")
    List<LogView> history(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.get();
        owned(id);
        return logs.findByTenantIdAndUserIdAndHabitIdOrderByLoggedAtDesc(user.tenantId(), user.userId(), id)
                .stream().map(this::logView).toList();
    }

    private Habit owned(UUID id) {
        CurrentUser user = CurrentUser.get();
        return habits.findByIdAndTenantIdAndUserId(id, user.tenantId(), user.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Habit not found"));
    }

    private void sync(ActivityLog logEntry) {
        try {
            restClient.post().uri(analyticsUrl + "/internal/analytics/activity").header("X-Internal-Key", internalKey)
                    .body(Map.of("logId", logEntry.id, "tenantId", logEntry.tenantId, "userId", logEntry.userId,
                            "habitId", logEntry.habitId, "loggedAt", logEntry.loggedAt))
                    .retrieve().toBodilessEntity();
            logEntry.analyticsSynced = true;
            logs.save(logEntry);
        } catch (RuntimeException ex) {
            log.warn("Analytics sync failed for activity log {}", logEntry.id);
        }
    }

    private void validateSchedule(String frequency, List<Integer> days) {
        if (frequency == null || !(frequency.equalsIgnoreCase("DAILY") || frequency.equalsIgnoreCase("WEEKLY"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "frequency must be DAILY or WEEKLY");
        }
        if (days != null && days.stream().anyMatch(day -> day == null || day < 1 || day > 7)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "daysOfWeek values must be between 1 and 7");
        }
        if (frequency.equalsIgnoreCase("WEEKLY") && (days == null || days.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Weekly habits require daysOfWeek");
        }
    }

    private String days(List<Integer> days) { return days == null ? "" : days.stream().distinct().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")); }
    private HabitView view(Habit habit) {
        List<Integer> dayList = habit.daysOfWeek == null || habit.daysOfWeek.isBlank() ? List.of()
                : java.util.Arrays.stream(habit.daysOfWeek.split(",")).map(Integer::parseInt).toList();
        return new HabitView(habit.id, habit.name, habit.category, habit.frequency, dayList, habit.preferredTime, habit.createdAt);
    }
    private LogView logView(ActivityLog entry) { return new LogView(entry.id, entry.habitId, entry.loggedAt, entry.source); }

    public record HabitRequest(@NotBlank String name, @NotBlank String category, String frequency,
                               List<Integer> daysOfWeek, LocalTime preferredTime) {}
    public record ManualLogRequest(Instant loggedAt) {}
    public record HabitView(UUID id, String name, String category, String frequency, List<Integer> daysOfWeek, LocalTime preferredTime, Instant createdAt) {}
    public record LogView(UUID id, UUID habitId, Instant loggedAt, String source) {}
}
