package com.habitloop.challenge;

import com.habitloop.common.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ChallengeController {
    private static final Logger log = LoggerFactory.getLogger(ChallengeController.class);
    private final CommunityRepository communities;
    private final MemberRepository members;
    private final InviteRepository invites;
    private final ChallengeRepository challenges;
    private final ParticipantRepository participants;
    private final RestClient client;
    private final String userUrl;
    private final String activityUrl;
    private final String analyticsUrl;
    private final String internalKey;

    ChallengeController(CommunityRepository communities, MemberRepository members, InviteRepository invites,
                        ChallengeRepository challenges, ParticipantRepository participants, RestClient.Builder builder,
                        @Value("${USER_URL:http://localhost:8081}") String userUrl,
                        @Value("${ACTIVITY_URL:http://localhost:8082}") String activityUrl,
                        @Value("${ANALYTICS_URL:http://localhost:8085}") String analyticsUrl,
                        @Value("${INTERNAL_KEY:local-internal-key}") String internalKey) {
        this.communities = communities; this.members = members; this.invites = invites;
        this.challenges = challenges; this.participants = participants; this.client = builder.build();
        this.userUrl = userUrl; this.activityUrl = activityUrl; this.analyticsUrl = analyticsUrl; this.internalKey = internalKey;
    }

    @GetMapping("/communities")
    List<CommunityView> communityFeed() {
        CurrentUser user = CurrentUser.get();
        return communities.findByTenantId(user.tenantId()).stream()
                .filter(c -> "PUBLIC".equals(c.visibility) || isMember(c.id, user.userId()))
                .map(this::communityView).toList();
    }

    @PostMapping("/communities")
    CommunityView createCommunity(@Valid @RequestBody CommunityRequest body) {
        CurrentUser user = CurrentUser.get();
        String visibility = body.visibility().trim().toUpperCase(java.util.Locale.ROOT);
        if (!visibility.equals("PUBLIC") && !visibility.equals("PRIVATE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "visibility must be PUBLIC or PRIVATE");
        }
        Community community = new Community();
        community.id = UUID.randomUUID(); community.tenantId = user.tenantId(); community.name = body.name().trim();
        community.description = body.description(); community.visibility = visibility; community.createdBy = user.userId();
        community.createdAt = Instant.now();
        communities.save(community);
        CommunityMember owner = new CommunityMember();
        owner.id = UUID.randomUUID(); owner.tenantId = user.tenantId(); owner.communityId = community.id;
        owner.userId = user.userId(); owner.role = "OWNER"; owner.joinedAt = Instant.now(); members.save(owner);
        return communityView(community);
    }

    @GetMapping("/communities/{id}")
    CommunityDetail communityDetail(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.get();
        Community community = communities.findByIdAndTenantId(id, user.tenantId()).orElseThrow(this::notFound);
        if ("PRIVATE".equals(community.visibility) && !isMember(id, user.userId())) throw notFound();
        List<MemberView> communityMembers = members.findByCommunityId(id).stream()
                .map(m -> new MemberView(m.userId, m.role, m.joinedAt)).toList();
        List<ChallengeView> communityChallenges = challenges.findByTenantId(user.tenantId()).stream()
                .filter(challenge -> challenge.communityId.equals(id)).map(this::challengeView).toList();
        return new CommunityDetail(communityView(community), communityMembers, communityChallenges);
    }

    @PostMapping("/communities/{id}/join")
    CommunityView joinCommunity(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.get();
        Community community = communities.findByIdAndTenantId(id, user.tenantId()).orElseThrow(this::notFound);
        if (!"PUBLIC".equals(community.visibility)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Private communities require an invite");
        addMember(community, user.userId(), "MEMBER");
        return communityView(community);
    }

    @PostMapping("/communities/{id}/invites")
    InviteView invite(@PathVariable UUID id, @Valid @RequestBody InviteRequest body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.get();
        Community community = communities.findByIdAndTenantId(id, user.tenantId()).orElseThrow(this::notFound);
        requireOwner(community.id, user.userId());
        UserLookup target;
        try {
            target = client.get().uri(userUrl + "/api/users?email={email}", body.email().trim())
                    .header("Authorization", request.getHeader("Authorization")).retrieve().body(UserLookup.class);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "User service could not verify the invitee");
        }
        if (target == null || !target.tenantId().equals(user.tenantId())) throw notFound();
        CommunityInvite invite = new CommunityInvite();
        invite.id = UUID.randomUUID(); invite.tenantId = user.tenantId(); invite.communityId = id;
        invite.invitedUserId = target.id(); invite.invitedBy = user.userId(); invite.status = "PENDING";
        invites.save(invite);
        return new InviteView(invite.id, invite.communityId, invite.status);
    }

    @GetMapping("/invites")
    List<InviteView> myInvites() {
        CurrentUser user = CurrentUser.get();
        return invites.findByTenantIdAndInvitedUserIdAndStatus(user.tenantId(), user.userId(), "PENDING").stream()
                .map(i -> new InviteView(i.id, i.communityId, i.status)).toList();
    }

    @PostMapping("/invites/{id}/accept")
    @Transactional
    public InviteView accept(@PathVariable UUID id) { return respondInvite(id, "ACCEPTED"); }

    @PostMapping("/invites/{id}/decline")
    InviteView decline(@PathVariable UUID id) { return respondInvite(id, "DECLINED"); }

    @GetMapping("/challenges")
    List<ChallengeView> challengeFeed() {
        CurrentUser user = CurrentUser.get();
        return challenges.findByTenantId(user.tenantId()).stream()
                .filter(c -> isMember(c.communityId, user.userId())).map(this::challengeView).toList();
    }

    @PostMapping("/communities/{id}/challenges")
    ChallengeView createChallenge(@PathVariable UUID id, @Valid @RequestBody ChallengeRequest body) {
        CurrentUser user = CurrentUser.get();
        Community community = communities.findByIdAndTenantId(id, user.tenantId()).orElseThrow(this::notFound);
        requireOwner(id, user.userId());
        if (!body.endDate().isBefore(body.startDate()) && !body.habitCategory().isBlank()) {
            Challenge challenge = new Challenge();
            challenge.id = UUID.randomUUID(); challenge.tenantId = user.tenantId(); challenge.communityId = id;
            challenge.title = body.title().trim(); challenge.description = body.description();
            challenge.habitCategory = body.habitCategory().trim(); challenge.targetCount = body.targetCount();
            challenge.startDate = body.startDate(); challenge.endDate = body.endDate(); challenge.createdBy = user.userId();
            return challengeView(challenges.save(challenge));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Challenge category is required and endDate must not precede startDate");
    }

    @GetMapping("/challenges/{id}")
    ChallengeDetail challengeDetail(@PathVariable UUID id) {
        CurrentUser user = CurrentUser.get();
        Challenge challenge = challenges.findByIdAndTenantId(id, user.tenantId()).orElseThrow(this::notFound);
        requireMember(challenge.communityId, user.userId());
        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        for (Participant participant : participants.findByChallengeId(id)) {
            Integer count = null;
            try {
                CountResponse result = client.get().uri(analyticsUrl +
                                "/internal/analytics/habits/{habitId}/count?tenantId={tenantId}&userId={userId}&from={from}&to={to}",
                                participant.habitId, participant.tenantId, participant.userId, challenge.startDate, challenge.endDate)
                        .header("X-Internal-Key", internalKey).retrieve().body(CountResponse.class);
                count = result == null ? null : result.count();
            } catch (RuntimeException unavailable) {
                log.warn("Analytics progress unavailable for challenge {} participant ({})",
                        id, unavailable.getClass().getSimpleName());
                // An unavailable analytics service should not prevent the leaderboard from rendering.
            }
            leaderboard.add(new LeaderboardEntry(participant.userId, participant.userName, count, challenge.targetCount));
        }
        leaderboard.sort(Comparator.comparing(LeaderboardEntry::count, Comparator.nullsLast(Comparator.reverseOrder())));
        return new ChallengeDetail(challengeView(challenge), leaderboard);
    }

    @PostMapping("/challenges/{id}/join")
    ParticipantView joinChallenge(@PathVariable UUID id, @Valid @RequestBody JoinRequest body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.get();
        Challenge challenge = challenges.findByIdAndTenantId(id, user.tenantId()).orElseThrow(this::notFound);
        requireMember(challenge.communityId, user.userId());
        HabitLookup habit;
        try {
            habit = client.get().uri(activityUrl + "/api/habits/{id}", body.habitId())
                    .header("Authorization", request.getHeader("Authorization")).retrieve().body(HabitLookup.class);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Activity service could not verify habit ownership");
        }
        if (habit == null || habit.category() == null || habit.category().isBlank() || challenge.habitCategory == null
                || challenge.habitCategory.isBlank()
                || !habit.category().trim().equalsIgnoreCase(challenge.habitCategory.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Habit category must match the challenge category");
        }
        Participant participant = participants.findByChallengeIdAndUserId(id, user.userId()).orElseGet(Participant::new);
        participant.id = participant.id == null ? UUID.randomUUID() : participant.id;
        participant.tenantId = user.tenantId(); participant.challengeId = id; participant.userId = user.userId();
        participant.userName = user.name(); participant.habitId = habit.id(); participant.joinedAt = Instant.now();
        participant = participants.save(participant);
        return new ParticipantView(participant.userId, participant.userName, participant.habitId);
    }

    private InviteView respondInvite(UUID id, String status) {
        CurrentUser user = CurrentUser.get();
        CommunityInvite invite = invites.findByIdAndTenantIdAndInvitedUserId(id, user.tenantId(), user.userId()).orElseThrow(this::notFound);
        if (!"PENDING".equals(invite.status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Invite is no longer pending");
        invite.status = status;
        invites.save(invite);
        if ("ACCEPTED".equals(status)) {
            Community community = communities.findByIdAndTenantId(invite.communityId, user.tenantId()).orElseThrow(this::notFound);
            addMember(community, user.userId(), "MEMBER");
        }
        return new InviteView(invite.id, invite.communityId, invite.status);
    }

    private void addMember(Community community, UUID userId, String role) {
        if (members.findByCommunityIdAndUserId(community.id, userId).isPresent()) return;
        CommunityMember member = new CommunityMember();
        member.id = UUID.randomUUID(); member.tenantId = community.tenantId; member.communityId = community.id;
        member.userId = userId; member.role = role; member.joinedAt = Instant.now(); members.save(member);
    }
    private boolean isMember(UUID communityId, UUID userId) { return members.findByCommunityIdAndUserId(communityId, userId).isPresent(); }
    private void requireMember(UUID communityId, UUID userId) { if (!isMember(communityId, userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Community membership required"); }
    private void requireOwner(UUID communityId, UUID userId) {
        if (members.findByCommunityIdAndUserId(communityId, userId).filter(m -> "OWNER".equals(m.role)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Community owner required");
        }
    }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"); }
    private CommunityView communityView(Community c) { return new CommunityView(c.id, c.name, c.description, c.visibility, c.createdBy); }
    private ChallengeView challengeView(Challenge c) { return new ChallengeView(c.id, c.communityId, c.title, c.description, c.habitCategory, c.targetCount, c.startDate, c.endDate); }

    public record CommunityRequest(@NotBlank String name, String description, @NotBlank String visibility) {}
    public record InviteRequest(@Email @NotBlank String email) {}
    public record ChallengeRequest(@NotBlank String title, String description, @NotBlank String habitCategory,
                                   @Positive int targetCount, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}
    public record JoinRequest(@NotNull UUID habitId) {}
    public record CommunityView(UUID id, String name, String description, String visibility, UUID createdBy) {}
    public record CommunityDetail(CommunityView community, List<MemberView> members, List<ChallengeView> challenges) {}
    public record MemberView(UUID userId, String role, Instant joinedAt) {}
    public record InviteView(UUID id, UUID communityId, String status) {}
    public record ChallengeView(UUID id, UUID communityId, String title, String description, String habitCategory, int targetCount, LocalDate startDate, LocalDate endDate) {}
    public record ChallengeDetail(ChallengeView challenge, List<LeaderboardEntry> leaderboard) {}
    public record LeaderboardEntry(UUID userId, String name, Integer count, int targetCount) {}
    public record ParticipantView(UUID userId, String name, UUID habitId) {}
    public record UserLookup(UUID id, UUID tenantId, String name) {}
    public record HabitLookup(UUID id, String category) {}
    public record CountResponse(int count) {}
}
