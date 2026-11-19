package com.habitloop.activity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InternalActivityController {
    private static final Logger log = LoggerFactory.getLogger(InternalActivityController.class);
    private final HabitRepository habits;
    private final ActivityLogRepository logs;
    private final RestClient restClient;
    private final String analyticsUrl;
    private final String internalKey;

    InternalActivityController(HabitRepository habits, ActivityLogRepository logs, RestClient.Builder builder,
                               @Value("${ANALYTICS_URL:http://localhost:8085}") String analyticsUrl,
                               @Value("${INTERNAL_KEY:local-internal-key}") String internalKey) {
        this.habits = habits; this.logs = logs; this.restClient = builder.build();
        this.analyticsUrl = analyticsUrl; this.internalKey = internalKey;
    }

    @PostMapping("/internal/activity-logs")
    ActivityLogView receive(@Valid @RequestBody WearableLog request) {
        if (request.connectionId() == null || request.externalEventId() == null || request.externalEventId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "connectionId and externalEventId are required");
        }
        ActivityLog previous = logs.findByConnectionIdAndExternalEventId(request.connectionId(), request.externalEventId()).orElse(null);
        if (previous != null) return view(previous);
        boolean validHabit = habits.findByIdAndTenantIdAndUserId(request.habitId(), request.tenantId(), request.userId()).isPresent();
        if (!validHabit) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Habit not found for supplied tenant and user");
        ActivityLog entry = new ActivityLog();
        entry.id = request.logId() == null ? UUID.randomUUID() : request.logId();
        entry.tenantId = request.tenantId(); entry.userId = request.userId(); entry.habitId = request.habitId();
        entry.connectionId = request.connectionId(); entry.externalEventId = request.externalEventId();
        entry.loggedAt = request.loggedAt() == null ? Instant.now() : request.loggedAt();
        entry.source = "WEARABLE"; entry.analyticsSynced = false;
        try {
            entry = logs.saveAndFlush(entry);
        } catch (DataIntegrityViolationException duplicate) {
            return view(logs.findByConnectionIdAndExternalEventId(request.connectionId(), request.externalEventId()).orElseThrow());
        }
        sync(entry);
        return view(entry);
    }

    private void sync(ActivityLog entry) {
        try {
            restClient.post().uri(analyticsUrl + "/internal/analytics/activity").header("X-Internal-Key", internalKey)
                    .body(Map.of("logId", entry.id, "tenantId", entry.tenantId, "userId", entry.userId,
                            "habitId", entry.habitId, "loggedAt", entry.loggedAt)).retrieve().toBodilessEntity();
            entry.analyticsSynced = true;
            logs.save(entry);
        } catch (RuntimeException ex) {
            log.warn("Analytics sync failed for activity log {}", entry.id);
        }
    }

    private ActivityLogView view(ActivityLog entry) { return new ActivityLogView(entry.id, entry.loggedAt); }
    public record WearableLog(UUID logId, @NotNull UUID tenantId, @NotNull UUID userId, @NotNull UUID habitId,
                              @NotNull UUID connectionId, @NotBlank String externalEventId, @NotNull Instant loggedAt) {}
    public record ActivityLogView(UUID logId, Instant loggedAt) {}
}
