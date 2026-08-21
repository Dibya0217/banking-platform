package com.banking.gateway.filter;

import com.banking.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Slf4j
@Component
@Order(-1)
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/otp/send",
            "/api/v1/auth/otp/verify",
            "/api/v1/auth/refresh"
    );

    private final JwtProperties jwtProperties;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Allow public paths and actuator endpoints through without auth
        if (PUBLIC_PATHS.contains(path) || path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            SecretKey key = Keys.hmacShaKeyFor(
                    jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));

            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String roles = claims.get("roles", String.class);
            if (roles == null) {
                roles = "ROLE_CUSTOMER";
            }

            String jti = claims.get("jti", String.class);
            final String finalRoles = roles;

            // Check Redis blacklist for revoked tokens
            if (jti != null) {
                return redisTemplate.hasKey("auth:blacklist:" + jti)
                        .flatMap(blacklisted -> {
                            if (Boolean.TRUE.equals(blacklisted)) {
                                log.warn("Blacklisted JWT jti={} rejected for userId={}", jti, userId);
                                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                return exchange.getResponse().setComplete();
                            }
                            return proceedWithAuth(exchange, chain, userId, finalRoles);
                        })
                        .onErrorResume(e -> {
                            // On Redis failure, allow request through (fail-open) but log warning
                            log.warn("Redis blacklist check failed, allowing request: {}", e.getMessage());
                            return proceedWithAuth(exchange, chain, userId, finalRoles);
                        });
            }

            return proceedWithAuth(exchange, chain, userId, finalRoles);

        } catch (Exception e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private Mono<Void> proceedWithAuth(ServerWebExchange exchange, WebFilterChain chain,
                                        String userId, String roles) {
        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r
                        .header("X-User-Id", userId)
                        .header("X-User-Roles", roles))
                .build();
        return chain.filter(mutated);
    }
}
