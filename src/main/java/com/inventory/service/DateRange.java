package com.inventory.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/** A reporting period. {@code from} is inclusive and {@code to} is exclusive. */
public record DateRange(String label, LocalDateTime from, LocalDateTime to) {

    public static DateRange resolve(String range, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        String r = range == null ? "today" : range.toLowerCase(Locale.ROOT);
        return switch (r) {
            case "today" -> of("Today", today, today);
            case "week" -> of("This week", today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today);
            case "month" -> of("This month", today.withDayOfMonth(1), today);
            case "year" -> of("This year", today.withDayOfYear(1), today);
            case "all" -> of("All time", LocalDate.of(2000, 1, 1), LocalDate.of(2099, 12, 31));
            case "custom" -> {
                if (from == null || to == null) throw new IllegalArgumentException("Choose a start date and an end date.");
                if (to.isBefore(from)) throw new IllegalArgumentException("The end date cannot be before the start date.");
                yield of(from + " to " + to, from, to);
            }
            default -> throw new IllegalArgumentException("Unknown date range: " + range);
        };
    }

    private static DateRange of(String label, LocalDate first, LocalDate last) {
        return new DateRange(label, first.atStartOfDay(), last.plusDays(1).atStartOfDay());
    }

    public LocalDate firstDay() { return from.toLocalDate(); }
    public LocalDate lastDay() { return to.minusDays(1).toLocalDate(); }
}
