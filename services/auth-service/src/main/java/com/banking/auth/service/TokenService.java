package com.banking.auth.service;

import com.banking.auth.config.JwtProperties;
import com.banking.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String BLACKLIST_PREFIX = "auth:blacklist:";
    private static final String REFRESH_PREFIX   = "auth:refresh:";

    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final RedisTemplate<String, String> redisTemplate;

    public String generateAccessToken(UUID userId, String email, List<String> roles, List<UUID> accountIds) {
        return jwtUtil.generateAccessToken(userId, email, roles, accountIds);
    }

    public String generateRefreshToken(UUID userId) {
        String token = jwtUtil.generateRefreshToken(userId);
        String jti = jwtUtil.extractJti(token);
        storeRefreshToken(userId, jti);
        return token;
    }

    public void revokeToken(String jti, long remainingSeconds) {
        if (remainingSeconds > 0) {
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + jti,
                    "1",
                    Duration.ofSeconds(remainingSeconds)
            );
        }
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }

    public void storeRefreshToken(UUID userId, String jti) {
        redisTemplate.opsForValue().set(
                REFRESH_PREFIX + userId,
                jti,
                Duration.ofSeconds(jwtProperties.getRefreshTokenExpiry())
        );
    }

    public boolean verifyRefreshToken(UUID userId, String jti) {
        String stored = redisTemplate.opsForValue().get(REFRESH_PREFIX + userId);
        return jti.equals(stored);
    }

    public void deleteRefreshToken(UUID userId) {
        redisTemplate.delete(REFRESH_PREFIX + userId);
    }
}
