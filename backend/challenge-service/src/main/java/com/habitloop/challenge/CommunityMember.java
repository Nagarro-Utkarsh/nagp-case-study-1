package com.habitloop.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "community_members", uniqueConstraints = @UniqueConstraint(columnNames = {"community_id", "user_id"}))
public class CommunityMember {
    @Id public UUID id;
    @Column(name = "tenant_id", nullable = false) public UUID tenantId;
    @Column(name = "community_id", nullable = false) public UUID communityId;
    @Column(name = "user_id", nullable = false) public UUID userId;
    @Column(nullable = false) public String role;
    @Column(name = "joined_at", nullable = false) public Instant joinedAt;
    protected CommunityMember() {}
}
