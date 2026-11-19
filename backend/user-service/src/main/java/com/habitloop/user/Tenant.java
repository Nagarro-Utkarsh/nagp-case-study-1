package com.habitloop.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {
    @Id public UUID id;
    @Column(nullable = false, unique = true) public String slug;
    @Column(nullable = false) public String name;
    protected Tenant() {}
    Tenant(String slug, String name) { this.id = UUID.randomUUID(); this.slug = slug; this.name = name; }
}
