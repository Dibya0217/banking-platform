package com.banking.auth.service;

import com.banking.auth.config.JwtProperties;
import com.banking.auth.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenServiceTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private JwtUtil jwtUtil;
    private JwtProperties jwtProperties;
    private TokenService tokenService;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("banking-super-secret-jwt-key-change-in-production-256bits");
        jwtProperties.setAccessTokenExpiry(900L);
        jwtProperties.setRefreshTokenExpiry(604800L);

        jwtUtil = new JwtUtil(jwtProperties);
        tokenService = new TokenService(jwtUtil, jwtProperties, redisTemplate);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void generateAccessToken_shouldContainExpectedClaims() {
        String token = tokenService.generateAccessToken(USER_ID, "user@example.com",
                List.of("ROLE_CUSTOMER"), List.of());

        Claims claims = jwtUtil.validateAndExtract(token);
        assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(claims.get("roles", List.class)).contains("ROLE_CUSTOMER");
    }

    @Test
    void generateRefreshToken_shouldStoreJtiInRedis() {
        String token = tokenService.generateRefreshToken(USER_ID);

        assertThat(token).isNotBlank();
        Claims claims = jwtUtil.validateAndExtract(token);
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");

        verify(valueOps).set(
                eq("auth:refresh:" + USER_ID),
                eq(claims.getId()),
                eq(Duration.ofSeconds(604800L))
        );
    }

    @Test
    void revokeToken_shouldWriteToBlacklist() {
        String jti = UUID.randomUUID().toString();
        tokenService.revokeToken(jti, 600L);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCaptor.capture(), eq("1"), eq(Duration.ofSeconds(600L)));
        assertThat(keyCaptor.getValue()).isEqualTo("auth:blacklist:" + jti);
    }

    @Test
    void revokeToken_withZeroTtl_shouldNotWrite() {
        tokenService.revokeToken(UUID.randomUUID().toString(), 0L);
        verifyNoInteractions(valueOps);
    }

    @Test
    void isBlacklisted_shouldReturnTrue_whenKeyExists() {
        String jti = UUID.randomUUID().toString();
        when(redisTemplate.hasKey("auth:blacklist:" + jti)).thenReturn(true);

        assertThat(tokenService.isBlacklisted(jti)).isTrue();
    }

    @Test
    void isBlacklisted_shouldReturnFalse_whenKeyAbsent() {
        String jti = UUID.randomUUID().toString();
        when(redisTemplate.hasKey("auth:blacklist:" + jti)).thenReturn(false);

        assertThat(tokenService.isBlacklisted(jti)).isFalse();
    }

    @Test
    void verifyRefreshToken_shouldReturnTrue_whenJtiMatches() {
        String jti = UUID.randomUUID().toString();
        when(valueOps.get("auth:refresh:" + USER_ID)).thenReturn(jti);

        assertThat(tokenService.verifyRefreshToken(USER_ID, jti)).isTrue();
    }

    @Test
    void verifyRefreshToken_shouldReturnFalse_whenJtiMismatch() {
        when(valueOps.get("auth:refresh:" + USER_ID)).thenReturn("different-jti");

        assertThat(tokenService.verifyRefreshToken(USER_ID, "my-jti")).isFalse();
    }

    @Test
    void deleteRefreshToken_shouldDeleteKey() {
        tokenService.deleteRefreshToken(USER_ID);
        verify(redisTemplate).delete("auth:refresh:" + USER_ID);
    }
}
