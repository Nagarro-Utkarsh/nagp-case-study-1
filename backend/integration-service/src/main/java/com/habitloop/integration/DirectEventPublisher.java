package com.habitloop.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.common.PartnerEventNormalizer;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "INTEGRATION_PUBLISHER", havingValue = "direct", matchIfMissing = true)
class DirectEventPublisher implements EventPublisher {
    private final PartnerEventNormalizer normalizer = new PartnerEventNormalizer();
    private final ObjectMapper mapper;
    private final RestClient client;
    private final String activityUrl;
    private final String internalKey;
    private final PartnerEventRepository events;

    DirectEventPublisher(ObjectMapper mapper, RestClient.Builder builder, PartnerEventRepository events,
                         @Value("${ACTIVITY_URL:http://localhost:8082}") String activityUrl,
                         @Value("${INTERNAL_KEY:local-internal-key}") String internalKey) {
        this.mapper = mapper; this.client = builder.build(); this.events = events;
        this.activityUrl = activityUrl; this.internalKey = internalKey;
    }

    @Override public void publish(PartnerEventEntity event) {
        try {
            IncomingEvent incoming = mapper.convertValue(event.payload, IncomingEvent.class);
            var normalized = normalizer.normalize(new PartnerEventNormalizer.PartnerEvent(incoming.eventId(),
                    incoming.metric(), incoming.value(), incoming.occurredAt(), incoming.tenantId(),
                    incoming.userId(), incoming.habitId(), incoming.connectionId()), Instant.now());
            if (!normalized.valid()) {
                event.status = "REJECTED"; event.rejectReason = normalized.rejectReason(); event.published = true;
                events.save(event);
                return;
            }
            PartnerEventNormalizer.ActivityLogEvent result = normalized.event();
            client.post().uri(activityUrl + "/internal/activity-logs").header("X-Internal-Key", internalKey)
                    .body(Map.of("tenantId", result.tenantId(), "userId", result.userId(), "habitId", result.habitId(),
                            "connectionId", result.connectionId(), "externalEventId", result.externalEventId(),
                            "loggedAt", result.loggedAt())).retrieve().toBodilessEntity();
            event.status = "PROCESSED"; event.published = true; events.save(event);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Stored partner event could not be decoded", ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IncomingEvent(String eventId, String metric, Integer value, Instant occurredAt,
                                 java.util.UUID tenantId, java.util.UUID userId, java.util.UUID habitId,
                                 java.util.UUID connectionId) {}
}
