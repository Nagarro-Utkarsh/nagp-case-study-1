package com.habitloop.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habitloop.common.PartnerEventNormalizer;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class IntegrationNormalizerHandler implements RequestHandler<Map<String, Object>, Void> {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PartnerEventNormalizer normalizer = new PartnerEventNormalizer();
    private final HttpClient http = HttpClient.newHttpClient();
    private final String activityUrl = required("ACTIVITY_URL");
    private final String integrationUrl = required("INTEGRATION_URL");
    private final SecretsManagerClient secretsManager = SecretsManagerClient.create();
    private final String internalKey = loadSecret(required("INTERNAL_KEY_SECRET_ARN"));

    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        try {
            return process(event);
        } catch (Exception ex) {
            throw new IllegalStateException("Partner event processing failed", ex);
        }
    }

    private Void process(Map<String, Object> event) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        if (detail == null) throw new IllegalArgumentException("EventBridge detail is required");
        UUID partnerEventId = uuid(detail, "partnerEventId");
        String eventId = text(detail, "eventId");
        var normalized = normalizer.normalize(new PartnerEventNormalizer.PartnerEvent(eventId,
                text(detail, "metric"), integer(detail.get("value")), instant(detail.get("occurredAt")),
                uuid(detail, "tenantId"), uuid(detail, "userId"),
                uuid(detail, "habitId"), uuid(detail, "connectionId")), Instant.now());
        if (!normalized.valid()) {
            updateStatus(partnerEventId, "REJECTED", normalized.rejectReason());
            return null;
        }
        PartnerEventNormalizer.ActivityLogEvent result = normalized.event();
        Map<String, Object> activity = Map.of("tenantId", result.tenantId(), "userId", result.userId(),
                "habitId", result.habitId(), "connectionId", result.connectionId(),
                "externalEventId", result.externalEventId(), "loggedAt", result.loggedAt().toString());
        send("POST", activityUrl + "/internal/activity-logs", activity);
        updateStatus(partnerEventId, "PROCESSED", null);
        return null;
    }

    private void updateStatus(UUID id, String status, String reason) throws Exception {
        Map<String, Object> body = reason == null ? Map.of("status", status) : Map.of("status", status, "rejectReason", reason);
        send("PUT", integrationUrl + "/internal/integrations/events/" + id + "/status", body);
    }

    private void send(String method, String url, Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .header("X-Internal-Key", internalKey).method(method,
                        HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Internal service returned HTTP " + response.statusCode());
        }
    }

    private String loadSecret(String secretArn) {
        String secret = secretsManager.getSecretValue(request -> request.secretId(secretArn)).secretString();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("The internal-service secret has no string value");
        }
        return secret;
    }

    private String text(Map<String, Object> values, String name) {
        Object value = values.get(name);
        return value instanceof String text ? text : null;
    }
    private Integer integer(Object value) { return value instanceof Number number ? number.intValue() : null; }
    private Instant instant(Object value) { return value instanceof String text ? Instant.parse(text) : null; }
    private UUID uuid(Map<String, Object> values, String name) {
        String value = text(values, name);
        return value == null ? null : UUID.fromString(value);
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
