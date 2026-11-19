package com.habitloop.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity @Table(name = "invites")
public class CommunityInvite {
    @Id public UUID id;
    @Column(name = "tenant_id", nullable = false) public UUID tenantId;
    @Column(name = "community_id", nullable = false) public UUID communityId;
    @Column(name = "invited_user_id", nullable = false) public UUID invitedUserId;
    @Column(name = "invited_by", nullable = false) public UUID invitedBy;
    @Column(nullable = false) public String status;
    protected CommunityInvite() {}
}
