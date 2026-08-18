package com.banking.auth.service;

import com.banking.common.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private OtpService otpService;

    private static final String MOBILE  = "9876543210";
    private static final String PURPOSE = "LOGIN";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        otpService = new OtpService(redisTemplate);
    }

    @Test
    void generateAndStore_shouldReturnSixDigitOtp() {
        when(valueOps.get("auth:otp:attempts:" + MOBILE)).thenReturn(null);

        String otp = otpService.generateAndStore(MOBILE, PURPOSE);

        assertThat(otp).matches("\\d{6}");
        verify(valueOps).set(
                eq("auth:otp:" + MOBILE + ":" + PURPOSE),
                eq(otp),
                eq(Duration.ofSeconds(300))
        );
    }

    @Test
    void generateAndStore_whenRateLimited_shouldThrow() {
        when(valueOps.get("auth:otp:attempts:" + MOBILE)).thenReturn("5");

        assertThatThrownBy(() -> otpService.generateAndStore(MOBILE, PURPOSE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Too many OTP requests");
    }

    @Test
    void verify_withCorrectOtp_shouldDeleteKey() {
        String key = "auth:otp:" + MOBILE + ":" + PURPOSE;
        when(valueOps.get(key)).thenReturn("123456");

        otpService.verify(MOBILE, PURPOSE, "123456");

        verify(redisTemplate).delete(key);
    }

    @Test
    void verify_withExpiredOtp_shouldThrow() {
        when(valueOps.get("auth:otp:" + MOBILE + ":" + PURPOSE)).thenReturn(null);

        assertThatThrownBy(() -> otpService.verify(MOBILE, PURPOSE, "123456"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verify_withWrongOtp_shouldThrow() {
        when(valueOps.get("auth:otp:" + MOBILE + ":" + PURPOSE)).thenReturn("999999");

        assertThatThrownBy(() -> otpService.verify(MOBILE, PURPOSE, "123456"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid OTP");
    }

    @Test
    void isRateLimited_shouldReturnTrue_whenCountAtMax() {
        when(valueOps.get("auth:otp:attempts:" + MOBILE)).thenReturn("5");
        assertThat(otpService.isRateLimited(MOBILE)).isTrue();
    }

    @Test
    void isRateLimited_shouldReturnFalse_whenCountBelowMax() {
        when(valueOps.get("auth:otp:attempts:" + MOBILE)).thenReturn("3");
        assertThat(otpService.isRateLimited(MOBILE)).isFalse();
    }
}
