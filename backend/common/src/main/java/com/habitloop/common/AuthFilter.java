package com.habitloop.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC_PATHS = Set.of("/api/auth/signup", "/api/auth/login");
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JwtService jwtService;
    private final String internalKey;

    public AuthFilter(JwtService jwtService, @Value("${INTERNAL_KEY:local-internal-key}") String internalKey) {
        this.jwtService = jwtService;
        this.internalKey = internalKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        boolean sse = path.equals("/api/analytics/stream") && HttpMethod.GET.matches(request.getMethod());
        if (request.getParameter("token") != null && !sse && !HttpMethod.OPTIONS.matches(request.getMethod())) return false;
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || !path.startsWith("/api/") && !path.startsWith("/internal/")
                || path.equals("/actuator/health")
                || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")
                || PUBLIC_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        boolean sse = path.equals("/api/analytics/stream") && HttpMethod.GET.matches(request.getMethod());
        if (request.getParameter("token") != null && !sse) {
            unauthorized(response, "Query-string tokens are only accepted by the analytics stream");
            return;
        }
        if (path.startsWith("/internal/")) {
            if (!constantTimeEquals(internalKey, request.getHeader("X-Internal-Key"))) {
                unauthorized(response, "Invalid internal credential");
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        if (path.equals("/api/integrations/events") && HttpMethod.POST.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        String token = sse ? request.getParameter("token")
                : authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
        if (token == null || token.isBlank()) {
            unauthorized(response, "Bearer token required");
            return;
        }
        CurrentUser current;
        try {
            current = jwtService.validate(token);
        } catch (JwtException | IllegalArgumentException ex) {
            unauthorized(response, "Invalid or expired token");
            return;
        }
        CurrentUser.set(current);
        request.setAttribute(CurrentUser.class.getName(), current);
        try {
            chain.doFilter(request, response);
        } finally {
            CurrentUser.clear();
        }
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (supplied == null) return false;
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void unauthorized(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        JSON.writeValue(response.getOutputStream(), java.util.Map.of(
                "type", "about:blank", "title", "Unauthorized", "status", HttpServletResponse.SC_UNAUTHORIZED, "detail", detail));
    }
}
