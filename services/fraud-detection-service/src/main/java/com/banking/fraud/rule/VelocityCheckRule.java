package com.banking.fraud.rule;

import com.banking.fraud.entity.FraudAlertSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VelocityCheckRule implements FraudRule {

    static final String RULE_NAME = "VELOCITY_CHECK";
    private static final String KEY_PREFIX = "fraud:velocity:";

    private final StringRedisTemplate redisTemplate;

    @Value("${fraud.velocity.max-transactions-per-hour:10}")
    private int maxTransactionsPerHour;

    @Override
    public FraudRuleResult evaluate(FraudCheckContext context) {
        String key = buildKey(context.fromAccountId(), context.initiatedAt());

        // Add this transaction to the sorted set (score = epoch millis, member = transactionId)
        redisTemplate.opsForZSet().add(key, context.transactionId().toString(),
                context.initiatedAt().toEpochMilli());

        // Set TTL of 2 hours on first write to allow window to expire naturally
        if (Boolean.TRUE.equals(redisTemplate.getExpire(key) < 0
                ? Boolean.TRUE : Boolean.FALSE)) {
            redisTemplate.expire(key, Duration.ofHours(2));
        }
        redisTemplate.expire(key, Duration.ofHours(2));

        // Count transactions in the current hour window
        Instant hourStart = context.initiatedAt().truncatedTo(ChronoUnit.HOURS);
        Long count = redisTemplate.opsForZSet().count(key,
                hourStart.toEpochMilli(), context.initiatedAt().toEpochMilli());

        if (count != null && count > maxTransactionsPerHour) {
            return FraudRuleResult.fail(RULE_NAME,
                    "Account " + context.fromAccountId() + " made " + count
                            + " transactions this hour (max " + maxTransactionsPerHour + ")",
                    FraudAlertSeverity.HIGH);
        }
        return FraudRuleResult.pass(RULE_NAME);
    }

    private String buildKey(UUID accountId, Instant instant) {
        // Hour bucket key — one sorted set per account per hour
        String hourBucket = String.valueOf(instant.truncatedTo(ChronoUnit.HOURS).toEpochMilli());
        return KEY_PREFIX + accountId + ":" + hourBucket;
    }
}
