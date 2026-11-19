package com.habitloop.activity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface HabitRepository extends JpaRepository<Habit, UUID> {
    List<Habit> findByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId, UUID userId);
    Optional<Habit> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);
    Optional<Habit> findByIdAndTenantIdAndUserIdAndCategoryIsNotNull(UUID id, UUID tenantId, UUID userId);
}
interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {
    List<ActivityLog> findByTenantIdAndUserIdAndHabitIdOrderByLoggedAtDesc(UUID tenantId, UUID userId, UUID habitId);
    Optional<ActivityLog> findByConnectionIdAndExternalEventId(UUID connectionId, String externalEventId);
    List<ActivityLog> findTop100ByAnalyticsSyncedFalseOrderByLoggedAtAsc();
}
