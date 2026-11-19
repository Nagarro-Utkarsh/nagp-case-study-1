package com.habitloop.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ConnectionRepository extends JpaRepository<WearableConnection, UUID> {
    List<WearableConnection> findByTenantIdAndUserId(UUID tenantId, UUID userId);
    Optional<WearableConnection> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);
    Optional<WearableConnection> findByApiKeyHashAndStatus(String apiKeyHash, String status);
}
interface PartnerEventRepository extends JpaRepository<PartnerEventEntity, UUID> {
    Optional<PartnerEventEntity> findByConnectionIdAndExternalEventId(UUID connectionId, String externalEventId);
    List<PartnerEventEntity> findTop20ByConnectionIdOrderByReceivedAtDesc(UUID connectionId);
    List<PartnerEventEntity> findTop100ByPublishedFalseAndPublishAttemptsLessThanOrderByReceivedAtAsc(int maxAttempts);
}
