package com.banking.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceCacheService {

    private static final String KEY_PREFIX = "balance:";

    private final StringRedisTemplate redisTemplate;

    @Value("${account.balance-cache-ttl-seconds:30}")
    private long cacheTtlSeconds;

    public Optional<BigDecimal> getCachedBalance(UUID accountId) {
        String value = redisTemplate.opsForValue().get(key(accountId));
        if (value == null) return Optional.empty();
        return Optional.of(new BigDecimal(value));
    }

    public void cacheBalance(UUID accountId, BigDecimal balance) {
        redisTemplate.opsForValue().set(key(accountId), balance.toPlainString(),
                Duration.ofSeconds(cacheTtlSeconds));
        log.debug("Balance cached for accountId={}, balance={}", accountId, balance);
    }

    public void invalidateBalance(UUID accountId) {
        redisTemplate.delete(key(accountId));
        log.debug("Balance cache invalidated for accountId={}", accountId);
    }

    private String key(UUID accountId) {
        return KEY_PREFIX + accountId;
    }
}
