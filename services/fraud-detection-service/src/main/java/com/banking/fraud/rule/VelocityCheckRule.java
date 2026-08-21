package com.banking.fraud.rule;

import com.banking.fraud.entity.FraudAlertSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class VelocityCheckRule implements FraudRule {

    static final String RULE_NAME = "VELOCITY_CHECK";
    private static final String KEY_PREFIX = "fraud:velocity:";

    private final StringRedisTemplate redisTemplate;

    @Value("${fraud.velocity.max-transactions-per-hour:10}")
    private int velocityThreshold;

    @Override
    public FraudRuleResult evaluate(FraudCheckContext context) {
        String key = KEY_PREFIX + context.fromAccountId();
        long now = System.currentTimeMillis();
        long windowStart = now - Duration.ofHours(2).toMillis();

        // Add current transaction with timestamp as score
        redisTemplate.opsForZSet().add(key, String.valueOf(now), now);

        // Remove entries outside the 2-hour window
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // Only set TTL if key has no expiry yet (NX semantics via conditional check)
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            redisTemplate.expire(key, Duration.ofHours(2));
        }

        Long count = redisTemplate.opsForZSet().zCard(key);
        int txnCount = count != null ? count.intValue() : 0;

        if (txnCount > velocityThreshold) {
            return FraudRuleResult.fail(RULE_NAME,
                    "Transaction count " + txnCount + " exceeds threshold " + velocityThreshold + " in 2h window",
                    FraudAlertSeverity.HIGH);
        }
        return FraudRuleResult.pass(RULE_NAME);
    }
}
