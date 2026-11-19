package com.habitloop.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "habits")
public class Habit {
    @Id public UUID id;
    @Column(name = "tenant_id", nullable = false) public UUID tenantId;
    @Column(name = "user_id", nullable = false) public UUID userId;
    @Column(nullable = false) public String name;
    @Column(nullable = false) public String category;
    @Column(nullable = false) public String frequency;
    @Column(name = "days_of_week") public String daysOfWeek;
    @Column(name = "preferred_time") public LocalTime preferredTime;
    @Column(name = "created_at", nullable = false, updatable = false) public Instant createdAt;
    protected Habit() {}
}
