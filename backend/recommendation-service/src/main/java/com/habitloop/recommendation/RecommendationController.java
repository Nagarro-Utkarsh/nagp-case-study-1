package com.habitloop.recommendation;

import com.habitloop.common.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationRules rules = new RecommendationRules();
    private final RestClient client;
    private final String activityUrl;
    private final String analyticsUrl;
    private final String internalKey;

    RecommendationController(RestClient.Builder builder,
                             @Value("${ACTIVITY_URL:http://localhost:8082}") String activityUrl,
                             @Value("${ANALYTICS_URL:http://localhost:8085}") String analyticsUrl,
                             @Value("${INTERNAL_KEY:local-internal-key}") String internalKey) {
        this.client = builder.build(); this.activityUrl = activityUrl; this.analyticsUrl = analyticsUrl; this.internalKey = internalKey;
    }

    @GetMapping
    List<Recommendation> recommendations(HttpServletRequest request) {
        CurrentUser user = CurrentUser.get();
        String authorization = request.getHeader("Authorization");
        Habit[] habits = call(() -> client.get().uri(activityUrl + "/api/habits")
                .header("Authorization", authorization).retrieve().body(Habit[].class));
        if (habits == null) return List.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dashboard = (List<Map<String, Object>>) call(() -> client.get()
                .uri(analyticsUrl + "/api/analytics/dashboard").header("Authorization", authorization)
                .retrieve().body(List.class));
        Map<UUID, Map<String, Object>> stats = dashboard == null ? Map.of() : dashboard.stream()
                .filter(row -> row.get("habitId") != null)
                .collect(java.util.stream.Collectors.toMap(row -> UUID.fromString(row.get("habitId").toString()), row -> row));
        List<Recommendation> result = new ArrayList<>();
        for (Habit habit : habits) {
            Map<String, Object> summary = stats.getOrDefault(habit.id(), Map.of());
            double completion = summary.get("completionPercentage") instanceof Number number ? number.doubleValue() : 0;
            var frequencySuggestion = rules.suggestFrequency(habit.frequency(), completion);
            if (frequencySuggestion.isPresent()) {
                boolean reduce = "WEEKLY".equals(frequencySuggestion.get());
                result.add(new Recommendation(habit.id(), "FREQUENCY", habit.frequency(), frequencySuggestion.get(),
                        reduce ? "The habit was completed on fewer than 40% of the last seven days."
                                : "The weekly schedule is completed consistently; consider increasing frequency."));
            }
            Map<Integer, Integer> histogram = fetchHours(user.tenantId(), user.userId(), habit.id());
            if (habit.preferredTime() != null) {
                rules.suggestTimeWindow(histogram, habit.preferredTime()).ifPresent(suggested ->
                        result.add(new Recommendation(habit.id(), "TIMING", habit.preferredTime().toString(), suggested.toString(),
                                "Most recorded completions occur in this two-hour window.")));
            }
        }
        return result;
    }

    @PostMapping("/apply")
    Habit apply(@Valid @RequestBody ApplyRequest request, HttpServletRequest servletRequest) {
        String authorization = servletRequest.getHeader("Authorization");
        Habit current = call(() -> client.get().uri(activityUrl + "/api/habits/{id}", request.habitId())
                .header("Authorization", authorization).retrieve().body(Habit.class));
        String frequency = request.frequency() == null ? current.frequency() : request.frequency();
        List<Integer> days = request.daysOfWeek() == null ? current.daysOfWeek() : request.daysOfWeek();
        if ("WEEKLY".equalsIgnoreCase(frequency) && request.frequency() != null && request.daysOfWeek() == null) {
            days = List.of(1, 3, 5);
        }
        HabitUpdate update = new HabitUpdate(current.name(), current.category(), frequency, days,
                request.preferredTime() == null ? current.preferredTime() : request.preferredTime());
        return call(() -> client.put().uri(activityUrl + "/api/habits/{id}", request.habitId())
                .header("Authorization", authorization).body(update).retrieve().body(Habit.class));
    }

    private Map<Integer, Integer> fetchHours(UUID tenantId, UUID userId, UUID habitId) {
        try {
            Hours response = client.get().uri(analyticsUrl +
                            "/internal/analytics/habits/{habitId}/hours?tenantId={tenantId}&userId={userId}&days=28",
                            habitId, tenantId, userId).header("X-Internal-Key", internalKey).retrieve().body(Hours.class);
            if (response == null || response.hours() == null) return Map.of();
            Map<Integer, Integer> histogram = new java.util.HashMap<>();
            response.hours().forEach((hour, count) -> histogram.put(Integer.parseInt(hour), count));
            return histogram;
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Analytics service is unavailable");
        }
    }

    private <T> T call(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (ResponseStatusException ex) { throw ex; }
        catch (RuntimeException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "A required service is unavailable"); }
    }

    public record Recommendation(UUID habitId, String type, String current, String suggested, String reason) {}
    public record ApplyRequest(@NotNull UUID habitId, LocalTime preferredTime, String frequency, List<Integer> daysOfWeek) {}
    public record Habit(UUID id, String name, String category, String frequency, List<Integer> daysOfWeek,
                        LocalTime preferredTime) {}
    public record HabitUpdate(String name, String category, String frequency, List<Integer> daysOfWeek,
                              LocalTime preferredTime) {}
    public record Hours(Map<String, Integer> hours) {}
}
