package com.habitloop.recommendation;

import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

public class RecommendationRules {
    public Optional<String> suggestFrequency(String frequency, double completionPercentage) {
        if ("DAILY".equalsIgnoreCase(frequency) && completionPercentage < 40) return Optional.of("WEEKLY");
        if ("WEEKLY".equalsIgnoreCase(frequency) && completionPercentage > 90) return Optional.of("DAILY");
        return Optional.empty();
    }

    public Optional<LocalTime> suggestTimeWindow(Map<Integer, Integer> hourlyCounts, LocalTime preferredTime) {
        int samples = hourlyCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (samples < 5 || preferredTime == null || hourlyCounts.isEmpty()) return Optional.empty();
        int mostFrequentHour = hourlyCounts.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(entry -> -entry.getKey()))
                .orElseThrow().getKey();
        int suggestedWindow = mostFrequentHour / 2;
        if (suggestedWindow == preferredTime.getHour() / 2) return Optional.empty();
        return Optional.of(LocalTime.of(suggestedWindow * 2, 0));
    }
}
