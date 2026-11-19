package com.habitloop.integration;

import com.habitloop.common.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {
    private static final Logger log = LoggerFactory.getLogger(IntegrationController.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ConnectionRepository connections;
    private final PartnerEventRepository events;
    private final EventPublisher publisher;
    private final RestClient client;
    private final String activityUrl;
    private final String internalKey;

    IntegrationController(ConnectionRepository connections, PartnerEventRepository events, EventPublisher publisher,
                          RestClient.Builder builder,
                          @Value("${ACTIVITY_URL:http://localhost:8082}") String activityUrl,
                          @Value("${INTERNAL_KEY:local-internal-key}") String internalKey) {
        this.connections = connections; this.events = events; this.publisher = publisher;
        this.client = builder.build(); this.activityUrl = activityUrl; this.internalKey = internalKey;
    }

    @GetMapping("/partners")
    List<PartnerView> partners() {
        return List.of(new PartnerView("MOCK_WEARABLE", true), new PartnerView("FITBIT", false), new PartnerView("STRAVA", false));
    }

    @GetMapping("/connections")
    List<ConnectionView> listConnections() {
        CurrentUser user = CurrentUser.get();
        return connections.findByTenantIdAndUserId(user.tenantId(), user.userId()).stream().map(this::connectionView).toList();
    }

    @PostMapping("/connections")
    ConnectionCreated connect(@Valid @RequestBody ConnectRequest request, HttpServletRequest servletRequest) {
        CurrentUser user = CurrentUser.get();
        String partner = request.partner().trim().toUpperCase(java.util.Locale.ROOT);
        if (!partner.equals("MOCK_WEARABLE")) throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Partner integration is coming soon");
        verifyHabit(request.habitId(), servletRequest.getHeader("Authorization"));
        String apiKey = newApiKey();
        WearableConnection connection = new WearableConnection();
        connection.id = UUID.randomUUID(); connection.tenantId = user.tenantId(); connection.userId = user.userId();
        connection.partner = partner; connection.habitId = request.habitId(); connection.apiKeyHash = hash(apiKey);
        connection.status = "ACTIVE"; connection.connectedAt = Instant.now(); connections.save(connection);
        return new ConnectionCreated(connection.id, connection.partner, connection.habitId, apiKey, connection.connectedAt);
    }

    @DeleteMapping("/connections/{id}")
    ResponseEntity<Void> disconnect(@PathVariable UUID id) {
        WearableConnection connection = ownedConnection(id);
        connection.status = "DISCONNECTED"; connection.apiKeyHash = hash(UUID.randomUUID().toString());
        connections.save(connection);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/events")
    ResponseEntity<EventReceipt> receive(@RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                         @Valid @RequestBody PartnerEventRequest request) {
        WearableConnection connection = authenticatedConnection(apiKey);
        return acceptEvent(connection, request);
    }

    @PostMapping("/connections/{id}/simulate")
    ResponseEntity<EventReceipt> simulate(@PathVariable UUID id, @RequestBody(required = false) SimulateRequest request) {
        WearableConnection connection = ownedConnection(id);
        SimulateRequest body = request == null ? new SimulateRequest(null, null, null, null) : request;
        PartnerEventRequest event = new PartnerEventRequest(
                body.eventId() == null || body.eventId().isBlank() ? UUID.randomUUID().toString() : body.eventId(),
                body.metric() == null ? "steps" : body.metric(),
                body.value() == null ? 1000 + RANDOM.nextInt(9000) : body.value(),
                body.occurredAt() == null ? Instant.now() : body.occurredAt());
        return acceptEvent(connection, event);
    }

    @GetMapping("/connections/{id}/events")
    List<EventView> history(@PathVariable UUID id) {
        WearableConnection connection = ownedConnection(id);
        return events.findTop20ByConnectionIdOrderByReceivedAtDesc(connection.id).stream()
                .map(e -> new EventView(e.externalEventId, e.payload, e.status, e.rejectReason, e.publishAttempts, e.receivedAt)).toList();
    }

    private ResponseEntity<EventReceipt> acceptEvent(WearableConnection connection, PartnerEventRequest request) {
        if (request.eventId() == null || request.eventId().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId is required");
        PartnerEventEntity existing = events.findByConnectionIdAndExternalEventId(connection.id, request.eventId()).orElse(null);
        if (existing != null) return ResponseEntity.accepted().body(new EventReceipt(existing.id, existing.externalEventId, true));
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("eventId", request.eventId()); payload.put("metric", request.metric()); payload.put("value", request.value());
        payload.put("occurredAt", request.occurredAt() == null ? null : request.occurredAt().toString()); payload.put("tenantId", connection.tenantId);
        payload.put("userId", connection.userId); payload.put("habitId", connection.habitId); payload.put("connectionId", connection.id);
        PartnerEventEntity event = new PartnerEventEntity();
        event.id = UUID.randomUUID(); event.tenantId = connection.tenantId; event.connectionId = connection.id;
        event.externalEventId = request.eventId(); event.receivedAt = Instant.now(); event.status = "RECEIVED";
        event.published = false; event.publishAttempts = 0;
        payload.put("partnerEventId", event.id);
        try {
            event.payload = payload;
            event = events.saveAndFlush(event);
        } catch (DataIntegrityViolationException duplicate) {
            PartnerEventEntity stored = events.findByConnectionIdAndExternalEventId(connection.id, request.eventId()).orElseThrow();
            return ResponseEntity.accepted().body(new EventReceipt(stored.id, stored.externalEventId, true));
        }
        publish(event);
        return ResponseEntity.accepted().body(new EventReceipt(event.id, event.externalEventId, false));
    }

    private void publish(PartnerEventEntity event) {
        try {
            publisher.publish(event);
            if (!event.published) {
                event.published = true;
                events.save(event);
            }
        } catch (RuntimeException ex) {
            event.publishAttempts++;
            events.save(event);
            log.warn("Partner event publish attempt {} failed for event {}", event.publishAttempts, event.id);
        }
    }

    private void verifyHabit(UUID habitId, String authorization) {
        try {
            client.get().uri(activityUrl + "/api/habits/{id}", habitId).header("Authorization", authorization)
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Activity service could not verify habit ownership");
        }
    }

    private WearableConnection ownedConnection(UUID id) {
        CurrentUser user = CurrentUser.get();
        return connections.findByIdAndTenantIdAndUserId(id, user.tenantId(), user.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found"));
    }
    private WearableConnection authenticatedConnection(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wearable API key required");
        return connections.findByApiKeyHashAndStatus(hash(apiKey), "ACTIVE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid wearable API key"));
    }
    private ConnectionView connectionView(WearableConnection c) { return new ConnectionView(c.id, c.partner, c.habitId, c.status, c.connectedAt); }
    private String newApiKey() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String secret) {
        try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    public record ConnectRequest(@NotBlank String partner, @NotNull UUID habitId) {}
    public record PartnerEventRequest(String eventId, String metric, Integer value, Instant occurredAt) {}
    public record SimulateRequest(String metric, Integer value, Instant occurredAt, String eventId) {}
    public record PartnerView(String partner, boolean available) {}
    public record ConnectionView(UUID id, String partner, UUID habitId, String status, Instant connectedAt) {}
    public record ConnectionCreated(UUID id, String partner, UUID habitId, String apiKey, Instant connectedAt) {}
    public record EventReceipt(UUID id, String eventId, boolean duplicate) {}
    public record EventView(String eventId, Map<String, Object> payload, String status, String rejectReason, int publishAttempts, Instant receivedAt) {}
}
