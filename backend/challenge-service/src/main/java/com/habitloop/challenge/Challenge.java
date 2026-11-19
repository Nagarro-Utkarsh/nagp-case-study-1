package com.habitloop.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "challenges")
public class Challenge {
    @Id public UUID id;
    @Column(name = "tenant_id", nullable = false) public UUID tenantId;
    @Column(name = "community_id", nullable = false) public UUID communityId;
    @Column(nullable = false) public String title;
    public String description;
    @Column(name = "habit_category", nullable = false) public String habitCategory;
    @Column(name = "target_count", nullable = false) public int targetCount;
    @Column(name = "start_date", nullable = false) public LocalDate startDate;
    @Column(name = "end_date", nullable = false) public LocalDate endDate;
    @Column(name = "created_by", nullable = false) public UUID createdBy;
    protected Challenge() {}
}
