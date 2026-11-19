package com.habitloop.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_logs", uniqueConstraints = @UniqueConstraint(columnNames = {"connection_id", "external_event_id"}))
public class ActivityLog {
    @Id public UUID id;
    @Column(name = "tenant_id", nullable = false) public UUID tenantId;
    @Column(name = "user_id", nullable = false) public UUID userId;
    @Column(name = "habit_id", nullable = false) public UUID habitId;
    @Column(name = "logged_at", nullable = false) public Instant loggedAt;
    @Column(nullable = false) public String source;
    @Column(name = "connection_id") public UUID connectionId;
    @Column(name = "external_event_id") public String externalEventId;
    @Column(name = "analytics_synced", nullable = false) public boolean analyticsSynced;
    protected ActivityLog() {}
}
