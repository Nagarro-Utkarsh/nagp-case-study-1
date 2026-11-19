package com.habitloop.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthFilterTest {
    private static final String SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";
    private static final String INTERNAL = "internal-test-key";
    private JwtService jwt;
    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        jwt = new JwtService(SECRET);
        filter = new AuthFilter(jwt, INTERNAL);
    }

    @Test
    void allowsPublicAuthAndCorsPreflightWithoutCredentials() throws Exception {
        assertPassed("/api/auth/signup", "POST", null);
        assertPassed("/api/users/me", "OPTIONS", null);
    }

    @Test
    void requiresBearerJwtAndRejectsQueryTokensOutsideSse() throws Exception {
        assertUnauthorized("/api/users/me", "GET", null, null);
        String token = jwt.issue(UUID.randomUUID(), UUID.randomUUID(), "user@example.com", "User");
        assertUnauthorized("/api/users/me", "GET", "Bearer " + token, token);
    }

    @Test
    void acceptsQueryJwtOnlyForSseAndInternalKeyOnlyForInternalRoutes() throws Exception {
        String token = jwt.issue(UUID.randomUUID(), UUID.randomUUID(), "user@example.com", "User");
        assertPassed("/api/analytics/stream", "GET", token);
        assertUnauthorized("/api/analytics/stream", "GET", "Bearer " + token, null);
        MockHttpServletRequest internal = request("/internal/analytics/activity", "POST");
        internal.addHeader("X-Internal-Key", INTERNAL);
        invokeAndAssertPassed(internal);
        MockHttpServletRequest badInternal = request("/internal/analytics/activity", "POST");
        badInternal.addHeader("Authorization", "Bearer " + token);
        invokeAndAssertUnauthorized(badInternal);
    }

    @Test
    void rejectsExpiredAndTamperedJwtTokens() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder().subject(UUID.randomUUID().toString())
                .claim("tenantId", UUID.randomUUID().toString()).claim("email", "user@example.com")
                .claim("name", "User").issuedAt(java.util.Date.from(java.time.Instant.now().minusSeconds(7200)))
                .expiration(java.util.Date.from(java.time.Instant.now().minusSeconds(3600)))
                .signWith(key, Jwts.SIG.HS256).compact();
        assertUnauthorized("/api/users/me", "GET", "Bearer " + expired, null);
        String token = jwt.issue(UUID.randomUUID(), UUID.randomUUID(), "user@example.com", "User");
        assertUnauthorized("/api/users/me", "GET", "Bearer " + token + "tampered", null);
    }

    @Test
    void doesNotConvertControllerFailuresIntoAuthenticationFailures() throws Exception {
        String token = jwt.issue(UUID.randomUUID(), UUID.randomUUID(), "user@example.com", "User");
        MockHttpServletRequest request = request("/api/users/me", "GET");
        request.addHeader("Authorization", "Bearer " + token);
        assertThrows(IllegalArgumentException.class, () -> filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> { throw new IllegalArgumentException("controller failure"); }));
    }

    private void assertPassed(String path, String method, String token) throws Exception {
        MockHttpServletRequest request = request(path, method);
        if (token != null) {
            if (path.equals("/api/analytics/stream")) request.setParameter("token", token);
            else request.addHeader("Authorization", "Bearer " + token);
        }
        invokeAndAssertPassed(request);
    }

    private void assertUnauthorized(String path, String method, String authorization, String queryToken) throws Exception {
        MockHttpServletRequest request = request(path, method);
        if (authorization != null) request.addHeader("Authorization", authorization);
        if (queryToken != null) request.setParameter("token", queryToken);
        invokeAndAssertUnauthorized(request);
    }

    private void invokeAndAssertPassed(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertTrue(called.get());
        assertFalse(response.getStatus() >= 400);
    }

    private void invokeAndAssertUnauthorized(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        assertFalse(called.get());
        assertEquals(401, response.getStatus());
    }

    private MockHttpServletRequest request(String path, String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
