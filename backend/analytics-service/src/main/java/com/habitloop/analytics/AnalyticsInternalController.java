package com.habitloop.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class AnalyticsInternalController {
    private final AnalyticsStore store;
    AnalyticsInternalController(AnalyticsStore store) { this.store = store; }

    @PostMapping("/internal/analytics/users/signup")
    void signup(@Valid @RequestBody SignupRequest request) {
        store.registerSignup(request.tenantId(), request.userId(), request.signedUpAt());
    }

    @PostMapping("/internal/analytics/activity")
    void activity(@Valid @RequestBody ActivityRequest request) {
        store.applyActivity(request.logId(), request.tenantId(), request.userId(), request.habitId(), request.loggedAt());
    }

    @GetMapping("/internal/analytics/habits/{id}/count")
    Map<String, Object> count(@PathVariable UUID id, @RequestParam UUID tenantId, @RequestParam UUID userId,
                              @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return Map.of("habitId", id, "count", store.count(tenantId, userId, id, from, to));
    }

    @GetMapping("/internal/analytics/habits/{id}/hours")
    Map<String, Object> hours(@PathVariable UUID id, @RequestParam UUID tenantId, @RequestParam UUID userId,
                              @RequestParam(defaultValue = "28") int days) {
        if (days < 1 || days > 365) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be from 1 to 365");
        return store.hours(tenantId, userId, id, days);
    }

    public record SignupRequest(@NotNull UUID tenantId, @NotNull UUID userId, @NotNull Instant signedUpAt) {}
    public record ActivityRequest(@NotNull UUID logId, @NotNull UUID tenantId, @NotNull UUID userId,
                                  @NotNull UUID habitId, @NotNull Instant loggedAt) {}
}
