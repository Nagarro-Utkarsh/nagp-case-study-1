package com.habitloop.analytics;

import com.habitloop.common.CurrentUser;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsStore store;
    private final RestClient client;
    private final String activityUrl;

    AnalyticsController(AnalyticsStore store, RestClient.Builder builder,
                        @Value("${ACTIVITY_URL:http://localhost:8082}") String activityUrl) {
        this.store = store; this.client = builder.build(); this.activityUrl = activityUrl;
    }

    @GetMapping("/dashboard")
    List<Map<String, Object>> dashboard(org.springframework.web.context.request.WebRequest request) {
        CurrentUser user = CurrentUser.get();
        List<AnalyticsStore.HabitView> habits = fetchHabits(request.getHeader("Authorization"));
        return store.dashboard(user.tenantId(), user.userId(), habits);
    }

    @GetMapping("/habits/{id}/trend")
    Map<String, Object> trend(@PathVariable UUID id, @RequestParam(defaultValue = "30") int days) {
        if (days < 1 || days > 365) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be from 1 to 365");
        CurrentUser user = CurrentUser.get();
        return store.trend(user.tenantId(), user.userId(), id, days);
    }

    @GetMapping("/cohorts")
    List<Map<String, Object>> cohorts() { return store.cohorts(CurrentUser.get().tenantId()); }

    @GetMapping("/stream")
    SseEmitter stream() {
        CurrentUser user = CurrentUser.get();
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        var active = new java.util.concurrent.atomic.AtomicBoolean(true);
        emitter.onTimeout(() -> { active.set(false); emitter.complete(); });
        emitter.onCompletion(() -> active.set(false));
        emitter.onError(error -> active.set(false));
        CompletableFuture.runAsync(() -> pollUpdates(emitter, user, active));
        return emitter;
    }

    private void pollUpdates(SseEmitter emitter, CurrentUser user, java.util.concurrent.atomic.AtomicBoolean active) {
        try {
            String lastUpdate = store.lastUpdatedAt(user.tenantId(), user.userId());
            long lastHeartbeat = System.nanoTime();
            while (active.get() && Instant.now().getEpochSecond() < user.expiresAtEpochSecond()) {
                Thread.sleep(2000);
                String current = store.lastUpdatedAt(user.tenantId(), user.userId());
                if (current != null && !current.equals(lastUpdate)) {
                    emitter.send(SseEmitter.event().name("dashboard-updated").data(Map.of("updatedAt", current)));
                    lastUpdate = current;
                }
                if (System.nanoTime() - lastHeartbeat >= 20_000_000_000L) {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                    lastHeartbeat = System.nanoTime();
                }
            }
            emitter.complete();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (IOException | RuntimeException ex) {
            emitter.completeWithError(ex);
        }
    }

    private List<AnalyticsStore.HabitView> fetchHabits(String authorization) {
        try {
            AnalyticsStore.HabitView[] habits = client.get().uri(activityUrl + "/api/habits")
                    .header("Authorization", authorization).retrieve().body(AnalyticsStore.HabitView[].class);
            return habits == null ? List.of() : List.of(habits);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Activity service is unavailable");
        }
    }

}
