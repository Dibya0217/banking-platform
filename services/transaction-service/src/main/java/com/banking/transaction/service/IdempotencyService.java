package com.banking.transaction.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${transaction.idempotency-ttl-seconds:86400}")
    private long ttlSeconds;

    public <T> Optional<T> findExisting(String idempotencyKey, Class<T> type) {
        String raw = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, type));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize idempotent response for key={}", idempotencyKey, e);
            return Optional.empty();
        }
    }

    public <T> void store(String idempotencyKey, T response) {
        try {
            String value = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(KEY_PREFIX + idempotencyKey, value,
                    Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache idempotent response for key={}", idempotencyKey, e);
        }
    }
}
