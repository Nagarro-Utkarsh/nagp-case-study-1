package com.habitloop.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

@Configuration
class EventBridgeConfiguration {
    @Bean
    @ConditionalOnProperty(name = "INTEGRATION_PUBLISHER", havingValue = "eventbridge")
    EventBridgeClient eventBridgeClient() { return EventBridgeClient.create(); }
}
