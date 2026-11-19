package com.habitloop.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartnerEventNormalizerTest {
    private final PartnerEventNormalizer normalizer = new PartnerEventNormalizer();
    private final UUID tenant = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final UUID habit = UUID.randomUUID();
    private final UUID connection = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-11T12:00:00Z");

    @Test
    void acceptsSupportedRecentEventsAndNormalizesMetricCase() {
        var result = normalizer.normalize(event("STEPS", now.minusSeconds(1)), now);
        assertTrue(result.valid());
        assertEquals(now.minusSeconds(1), result.event().loggedAt());
    }

    @Test
    void rejectsUnknownMetricsFutureEventsAndOldEvents() {
        assertEquals("Unsupported metric", normalizer.normalize(event("distance", now), now).rejectReason());
        assertEquals("Event timestamp is in the future", normalizer.normalize(event("steps", now.plusSeconds(1)), now).rejectReason());
        assertEquals("Event is older than 7 days", normalizer.normalize(event("steps", now.minusSeconds(7 * 86400 + 1)), now).rejectReason());
    }

    @Test
    void rejectsMissingIdentityOrNonPositiveMeasurement() {
        assertFalse(normalizer.normalize(new PartnerEventNormalizer.PartnerEvent("id", "steps", 10, now,
                null, user, habit, connection), now).valid());
        assertFalse(normalizer.normalize(event("steps", now, 0), now).valid());
    }

    private PartnerEventNormalizer.PartnerEvent event(String metric, Instant time) {
        return event(metric, time, 42);
    }
    private PartnerEventNormalizer.PartnerEvent event(String metric, Instant time, int value) {
        return new PartnerEventNormalizer.PartnerEvent("external-id", metric, value, time,
                tenant, user, habit, connection);
    }
}
