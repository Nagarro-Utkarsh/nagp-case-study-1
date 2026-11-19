package com.habitloop.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

public class PartnerEventNormalizer {
    private static final Set<String> METRICS = Set.of("steps", "workout");
    private static final Duration MAX_AGE = Duration.ofDays(7);

    public NormalizedEvent normalize(PartnerEvent event, Instant now) {
        if (event == null || blank(event.eventId()) || blank(event.metric()) || event.value() == null
                || event.value() <= 0 || event.occurredAt() == null || event.tenantId() == null
                || event.userId() == null || event.habitId() == null || event.connectionId() == null) {
            return NormalizedEvent.rejected("Required event fields are missing or invalid");
        }
        if (!METRICS.contains(event.metric().trim().toLowerCase(java.util.Locale.ROOT))) {
            return NormalizedEvent.rejected("Unsupported metric");
        }
        if (event.occurredAt().isAfter(now)) return NormalizedEvent.rejected("Event timestamp is in the future");
        if (event.occurredAt().isBefore(now.minus(MAX_AGE))) return NormalizedEvent.rejected("Event is older than 7 days");
        return NormalizedEvent.accepted(new ActivityLogEvent(event.tenantId(), event.userId(), event.habitId(),
                event.connectionId(), event.eventId(), event.occurredAt()));
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    public record PartnerEvent(String eventId, String metric, Integer value, Instant occurredAt,
                               java.util.UUID tenantId, java.util.UUID userId, java.util.UUID habitId,
                               java.util.UUID connectionId) {}
    public record ActivityLogEvent(java.util.UUID tenantId, java.util.UUID userId, java.util.UUID habitId,
                                   java.util.UUID connectionId, String externalEventId, Instant loggedAt) {}
    public record NormalizedEvent(boolean valid, String rejectReason, ActivityLogEvent event) {
        public static NormalizedEvent accepted(ActivityLogEvent event) { return new NormalizedEvent(true, null, event); }
        public static NormalizedEvent rejected(String reason) { return new NormalizedEvent(false, reason, null); }
    }
}
