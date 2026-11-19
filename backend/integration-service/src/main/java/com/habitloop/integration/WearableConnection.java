package com.habitloop.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "connections")
public class WearableConnection {
    @Id public UUID id;
    @Column(name = "tenant_id", nullable = false) public UUID tenantId;
    @Column(name = "user_id", nullable = false) public UUID userId;
    @Column(nullable = false) public String partner;
    @Column(name = "habit_id", nullable = false) public UUID habitId;
    @Column(name = "api_key_hash", nullable = false, unique = true) public String apiKeyHash;
    @Column(nullable = false) public String status;
    @Column(name = "connected_at", nullable = false) public Instant connectedAt;
    protected WearableConnection() {}
}
