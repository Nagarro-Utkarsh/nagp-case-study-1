package com.habitloop.analytics;

import java.time.LocalDate;
import java.util.Set;

public class StreakCalculator {
    public Result calculate(Set<LocalDate> days, LocalDate today) {
        if (days.isEmpty()) return new Result(0, 0, null);
        var sorted = days.stream().sorted().toList();
        int longest = 1;
        int run = 1;
        for (int i = 1; i < sorted.size(); i++) {
            run = sorted.get(i - 1).plusDays(1).equals(sorted.get(i)) ? run + 1 : 1;
            longest = Math.max(longest, run);
        }
        LocalDate last = sorted.getLast();
        int current = 1;
        for (int i = sorted.size() - 2; i >= 0 && sorted.get(i).plusDays(1).equals(sorted.get(i + 1)); i--) current++;
        if (last.isBefore(today.minusDays(1))) current = 0;
        return new Result(current, longest, last);
    }

    public record Result(int current, int longest, LocalDate lastDate) {}
}
