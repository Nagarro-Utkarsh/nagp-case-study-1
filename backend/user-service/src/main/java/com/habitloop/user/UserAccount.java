package com.habitloop.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "email"}))
public class UserAccount {
    @Id public UUID id;
    @Column(name = "tenant_id", nullable = false) public UUID tenantId;
    @Column(nullable = false) public String email;
    @Column(name = "password_hash", nullable = false) public String passwordHash;
    @Column(nullable = false) public String name;
    public String timezone;
    @Column(name = "created_at", nullable = false, updatable = false) public Instant createdAt;
    protected UserAccount() {}
}
