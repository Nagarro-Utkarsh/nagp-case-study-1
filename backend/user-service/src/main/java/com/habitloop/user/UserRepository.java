package com.habitloop.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TenantRepository extends JpaRepository<Tenant, UUID> { Optional<Tenant> findBySlug(String slug); }

interface UserRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByTenantIdAndEmail(UUID tenantId, String email);
    Optional<UserAccount> findByIdAndTenantId(UUID id, UUID tenantId);
}
