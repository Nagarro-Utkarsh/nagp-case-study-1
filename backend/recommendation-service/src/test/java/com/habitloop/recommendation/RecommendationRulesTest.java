package com.habitloop.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecommendationRulesTest {
    private final RecommendationRules rules = new RecommendationRules();

    @Test
    void suggestsFrequencyOnlyOutsideThresholds() {
        assertEquals("WEEKLY", rules.suggestFrequency("DAILY", 39.99).orElseThrow());
        assertEquals("DAILY", rules.suggestFrequency("WEEKLY", 90.01).orElseThrow());
        assertTrue(rules.suggestFrequency("DAILY", 40).isEmpty());
        assertTrue(rules.suggestFrequency("WEEKLY", 90).isEmpty());
    }

    @Test
    void suggestsDifferentTwoHourWindowOnlyWithFiveSamples() {
        assertEquals(LocalTime.of(18, 0), rules.suggestTimeWindow(Map.of(18, 5), LocalTime.of(8, 0)).orElseThrow());
        assertTrue(rules.suggestTimeWindow(Map.of(18, 4), LocalTime.of(8, 0)).isEmpty());
        assertTrue(rules.suggestTimeWindow(Map.of(9, 5), LocalTime.of(8, 30)).isEmpty());
    }
}
