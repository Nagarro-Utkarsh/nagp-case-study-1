package com.habitloop.analytics;

import java.net.URI;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
class DynamoConfiguration {
    @Bean
    DynamoDbClient dynamoDbClient(@Value("${AWS_REGION:us-east-1}") String region,
                                  @Value("${DYNAMODB_ENDPOINT:}") String endpoint) {
        var builder = DynamoDbClient.builder().region(Region.of(region));
        if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }
}
