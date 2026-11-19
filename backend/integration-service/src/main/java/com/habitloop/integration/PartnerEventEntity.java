package com.habitloop.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "partner_events", uniqueConstraints = @UniqueConstraint(columnNames = {"connection_id", "external_event_id"}))
public class PartnerEventEntity {
    @Id public UUID id;
    @Column(name = "tenant_id", nullable = false) public UUID tenantId;
    @Column(name = "connection_id", nullable = false) public UUID connectionId;
    @Column(name = "external_event_id", nullable = false) public String externalEventId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false) public Map<String, Object> payload;
    @Column(name = "received_at", nullable = false) public Instant receivedAt;
    @Column(nullable = false) public String status;
    @Column(name = "reject_reason") public String rejectReason;
    @Column(nullable = false) public boolean published;
    @Column(name = "publish_attempts", nullable = false) public int publishAttempts;
    protected PartnerEventEntity() {}
}
