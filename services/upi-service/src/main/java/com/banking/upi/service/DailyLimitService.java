package com.banking.upi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyLimitService {

    private static final String KEY_PREFIX = "upi:daily:";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final StringRedisTemplate redisTemplate;

    public BigDecimal getUsedToday(String vpa) {
        String key = buildKey(vpa);
        String raw = redisTemplate.opsForValue().get(key);
        return raw == null ? BigDecimal.ZERO : new BigDecimal(raw);
    }

    /**
     * Atomically adds {@code amount} to today's usage and returns the new total.
     * TTL is set to seconds remaining until midnight IST on first use.
     */
    public BigDecimal addUsage(String vpa, BigDecimal amount) {
        String key = buildKey(vpa);
        Double newTotal = redisTemplate.opsForValue().increment(key, amount.doubleValue());

        // Set TTL only on first write (TTL == -1 means key exists with no TTL)
        Long ttl = redisTemplate.getExpire(key);
        if (ttl != null && ttl < 0) {
            redisTemplate.expire(key, secondsUntilMidnight());
            log.debug("Set daily limit TTL for key={}, seconds={}", key, secondsUntilMidnight().getSeconds());
        }

        return newTotal == null ? amount : BigDecimal.valueOf(newTotal);
    }

    public void resetUsage(String vpa) {
        redisTemplate.delete(buildKey(vpa));
    }

    private String buildKey(String vpa) {
        return KEY_PREFIX + vpa + ":" + LocalDate.now(IST);
    }

    private Duration secondsUntilMidnight() {
        ZonedDateTime now = ZonedDateTime.now(IST);
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(IST);
        return Duration.between(now, midnight);
    }
}
