package com.habitloop.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtService {
    private final SecretKey key;

    public JwtService(@Value("${JWT_SECRET:local-development-secret-change-me-32-bytes-minimum}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(UUID userId, UUID tenantId, String email, String name) {
        Instant now = Instant.now();
        return Jwts.builder().subject(userId.toString()).claim("tenantId", tenantId.toString())
                .claim("email", email).claim("name", name).issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofHours(12)))).signWith(key, Jwts.SIG.HS256).compact();
    }

    public CurrentUser validate(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        try {
            UUID userId = UUID.fromString(claims.getSubject());
            UUID tenantId = UUID.fromString(claims.get("tenantId", String.class));
            String email = claims.get("email", String.class);
            String name = claims.get("name", String.class);
            Date expiration = claims.getExpiration();
            if (email == null || email.isBlank() || name == null || name.isBlank() || expiration == null) {
                throw new IllegalArgumentException("Missing required JWT claims");
            }
            return new CurrentUser(userId, tenantId, email, name, expiration.toInstant().getEpochSecond());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid JWT claims", ex);
        }
    }
}
