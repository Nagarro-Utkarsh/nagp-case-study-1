package com.habitloop.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StreakCalculatorTest {
    private final StreakCalculator calculator = new StreakCalculator();
    private final LocalDate today = LocalDate.parse("2026-11-11");

    @Test
    void computesCurrentAndLongestStreakForConsecutiveDays() {
        var result = calculator.calculate(Set.of(today.minusDays(3), today.minusDays(2), today.minusDays(1)), today);
        assertEquals(3, result.current());
        assertEquals(3, result.longest());
        assertEquals(today.minusDays(1), result.lastDate());
    }

    @Test
    void keepsLongestStreakButExpiresCurrentAfterOneMissedDay() {
        var result = calculator.calculate(Set.of(today.minusDays(10), today.minusDays(9), today.minusDays(5)), today);
        assertEquals(0, result.current());
        assertEquals(2, result.longest());
    }
}
