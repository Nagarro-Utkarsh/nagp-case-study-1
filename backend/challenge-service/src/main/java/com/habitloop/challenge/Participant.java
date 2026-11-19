package com.habitloop.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "participants", uniqueConstraints = @UniqueConstraint(columnNames = {"challenge_id", "user_id"}))
public class Participant {
    @Id public UUID id;
    @Column(name = "tenant_id", nullable = false) public UUID tenantId;
    @Column(name = "challenge_id", nullable = false) public UUID challengeId;
    @Column(name = "user_id", nullable = false) public UUID userId;
    @Column(name = "user_name", nullable = false) public String userName;
    @Column(name = "habit_id", nullable = false) public UUID habitId;
    @Column(name = "joined_at", nullable = false) public Instant joinedAt;
    protected Participant() {}
}
