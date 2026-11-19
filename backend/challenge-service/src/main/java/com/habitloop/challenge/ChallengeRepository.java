package com.habitloop.challenge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CommunityRepository extends JpaRepository<Community, UUID> { List<Community> findByTenantId(UUID tenantId); Optional<Community> findByIdAndTenantId(UUID id, UUID tenantId); }
interface MemberRepository extends JpaRepository<CommunityMember, UUID> {
    Optional<CommunityMember> findByCommunityIdAndUserId(UUID communityId, UUID userId);
    List<CommunityMember> findByTenantIdAndUserId(UUID tenantId, UUID userId);
    List<CommunityMember> findByCommunityId(UUID communityId);
}
interface InviteRepository extends JpaRepository<CommunityInvite, UUID> { List<CommunityInvite> findByTenantIdAndInvitedUserIdAndStatus(UUID tenantId, UUID userId, String status); Optional<CommunityInvite> findByIdAndTenantIdAndInvitedUserId(UUID id, UUID tenantId, UUID userId); }
interface ChallengeRepository extends JpaRepository<Challenge, UUID> { List<Challenge> findByTenantId(UUID tenantId); Optional<Challenge> findByIdAndTenantId(UUID id, UUID tenantId); }
interface ParticipantRepository extends JpaRepository<Participant, UUID> { List<Participant> findByChallengeId(UUID challengeId); Optional<Participant> findByChallengeIdAndUserId(UUID challengeId, UUID userId); }
