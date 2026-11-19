package com.habitloop.user;

import com.habitloop.common.CurrentUser;
import com.habitloop.common.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;

@RestController
@RequestMapping("/api")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final TenantRepository tenants;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwtService;
    private final RestClient restClient;
    private final String analyticsUrl;
    private final String internalKey;

    UserController(TenantRepository tenants, UserRepository users, PasswordEncoder passwords, JwtService jwtService,
                   RestClient.Builder builder, @Value("${ANALYTICS_URL:http://localhost:8085}") String analyticsUrl,
                   @Value("${INTERNAL_KEY:local-internal-key}") String internalKey) {
        this.tenants = tenants; this.users = users; this.passwords = passwords; this.jwtService = jwtService;
        this.restClient = builder.build(); this.analyticsUrl = analyticsUrl; this.internalKey = internalKey;
    }

    @PostMapping("/auth/signup")
    public Map<String, String> signup(@Valid @RequestBody SignupRequest request) {
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
        String tenantSlug = request.tenantSlug() == null || request.tenantSlug().isBlank() ? "public" : request.tenantSlug().trim();
        Tenant tenant = tenants.findBySlug(tenantSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown tenant"));
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (users.findByTenantIdAndEmail(tenant.id, email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        UserAccount user = new UserAccount();
        user.id = UUID.randomUUID(); user.tenantId = tenant.id; user.email = email;
        user.passwordHash = passwords.encode(request.password()); user.name = request.name().trim();
        user.timezone = "UTC"; user.createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        users.saveAndFlush(user);
        // The repository transaction commits before this best-effort cross-service notification.
        registerSignup(user);
        return Map.of("token", jwtService.issue(user.id, user.tenantId, user.email, user.name));
    }

    @PostMapping("/auth/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest request) {
        String tenantSlug = request.tenantSlug() == null || request.tenantSlug().isBlank() ? "public" : request.tenantSlug().trim();
        Tenant tenant = tenants.findBySlug(tenantSlug).orElse(null);
        UserAccount user = tenant == null ? null : users.findByTenantIdAndEmail(tenant.id,
                request.email().trim().toLowerCase(java.util.Locale.ROOT)).orElse(null);
        if (user == null || !passwords.matches(request.password(), user.passwordHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return Map.of("token", jwtService.issue(user.id, user.tenantId, user.email, user.name));
    }

    @GetMapping("/users/me")
    public Profile me() {
        CurrentUser current = CurrentUser.get();
        UserAccount user = users.findByIdAndTenantId(current.userId(), current.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return profile(user);
    }

    @PutMapping("/users/me")
    public Profile updateMe(@Valid @RequestBody ProfileUpdate request) {
        CurrentUser current = CurrentUser.get();
        UserAccount user = users.findByIdAndTenantId(current.userId(), current.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        user.name = request.name().trim();
        user.timezone = request.timezone() == null || request.timezone().isBlank() ? "UTC" : request.timezone().trim();
        return profile(users.save(user));
    }

    @GetMapping("/users")
    public Profile findByEmail(@RequestParam @Email String email) {
        CurrentUser current = CurrentUser.get();
        UserAccount user = users.findByTenantIdAndEmail(current.tenantId(), email.trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return profile(user);
    }

    private void registerSignup(UserAccount user) {
        try {
            restClient.post().uri(analyticsUrl + "/internal/analytics/users/signup")
                    .header("X-Internal-Key", internalKey)
                    .body(Map.of("tenantId", user.tenantId, "userId", user.id, "signedUpAt", user.createdAt))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("Analytics signup registration failed for tenant {} user {}", user.tenantId, user.id);
        }
    }

    private Profile profile(UserAccount user) {
        return new Profile(user.id, user.tenantId, user.email, user.name, user.timezone, user.createdAt);
    }

    public record SignupRequest(@Email @NotBlank String email, @NotBlank @Size(min = 8, max = 72) String password,
                                @NotBlank @Size(max = 100) String name, String tenantSlug) {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password, String tenantSlug) {}
    public record ProfileUpdate(@NotBlank @Size(max = 100) String name, String timezone) {}
    public record Profile(UUID id, UUID tenantId, String email, String name, String timezone, Instant createdAt) {}
}
