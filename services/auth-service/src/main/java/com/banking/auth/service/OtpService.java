package com.banking.auth.service;

import com.banking.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final String OTP_PREFIX      = "auth:otp:";
    private static final String ATTEMPTS_PREFIX = "auth:otp:attempts:";
    private static final int    MAX_ATTEMPTS    = 5;
    private static final int    OTP_TTL_SECONDS = 300;
    private static final int    RATE_LIMIT_TTL  = 3600;

    private final RedisTemplate<String, String> redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateAndStore(String mobile, String purpose) {
        if (isRateLimited(mobile)) {
            throw new BusinessRuleException("OTP_RATE_LIMIT", "Too many OTP requests. Try again after 1 hour.");
        }
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        String key = otpKey(mobile, purpose);
        redisTemplate.opsForValue().set(key, otp, Duration.ofSeconds(OTP_TTL_SECONDS));
        incrementRateCounter(mobile);
        return otp;
    }

    public void verify(String mobile, String purpose, String submitted) {
        String key = otpKey(mobile, purpose);
        String stored = redisTemplate.opsForValue().get(key);

        if (stored == null) {
            throw new BusinessRuleException("OTP_EXPIRED", "OTP has expired or was never sent.");
        }
        if (!stored.equals(submitted)) {
            throw new BusinessRuleException("OTP_INVALID", "Invalid OTP.");
        }
        redisTemplate.delete(key);
    }

    public boolean isRateLimited(String mobile) {
        String count = redisTemplate.opsForValue().get(ATTEMPTS_PREFIX + mobile);
        return count != null && Integer.parseInt(count) >= MAX_ATTEMPTS;
    }

    private void incrementRateCounter(String mobile) {
        String key = ATTEMPTS_PREFIX + mobile;
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(RATE_LIMIT_TTL));
        }
    }

    private String otpKey(String mobile, String purpose) {
        return OTP_PREFIX + mobile + ":" + purpose;
    }
}
