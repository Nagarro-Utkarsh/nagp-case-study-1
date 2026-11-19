package com.habitloop.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

@Component
@ConditionalOnProperty(name = "INTEGRATION_PUBLISHER", havingValue = "eventbridge")
class AwsEventPublisher implements EventPublisher {
    private final EventBridgeClient client;
    private final ObjectMapper mapper;
    private final String busName;
    AwsEventPublisher(EventBridgeClient client, ObjectMapper mapper, @Value("${EVENT_BUS_NAME:default}") String busName) {
        this.client = client; this.mapper = mapper; this.busName = busName;
    }

    @Override public void publish(PartnerEventEntity event) {
        try {
            var response = client.putEvents(PutEventsRequest.builder().entries(PutEventsRequestEntry.builder()
                    .eventBusName(busName).source("habitloop.integration").detailType("PartnerEventReceived")
                    .detail(mapper.writeValueAsString(event.payload)).build()).build());
            if (response.failedEntryCount() != null && response.failedEntryCount() > 0) {
                throw new IllegalStateException("EventBridge rejected the partner event");
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize EventBridge event", ex);
        }
    }
}
