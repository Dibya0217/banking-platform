package com.banking.fraud.rule;

import com.banking.fraud.entity.FraudAlertSeverity;
import com.banking.fraud.service.BlacklistCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class FraudRuleChainTest {

    @Mock
    private BlacklistCacheService blacklistCacheService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private BlacklistCheckRule blacklistCheckRule;
    private VelocityCheckRule velocityCheckRule;
    private LargeTransactionRule largeTransactionRule;
    private FraudRuleChain fraudRuleChain;

    private final UUID accountId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        blacklistCheckRule = new BlacklistCheckRule(blacklistCacheService);

        velocityCheckRule = new VelocityCheckRule(redisTemplate);
        ReflectionTestUtils.setField(velocityCheckRule, "maxTransactionsPerHour", 10);

        largeTransactionRule = new LargeTransactionRule();
        ReflectionTestUtils.setField(largeTransactionRule, "threshold", new BigDecimal("500000"));

        fraudRuleChain = new FraudRuleChain(blacklistCheckRule, velocityCheckRule, largeTransactionRule);

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(redisTemplate.getExpire(anyString())).thenReturn(7200L);
    }

    @Test
    void allRulesPass_whenAccountIsClean_amountBelowThreshold_velocityWithinLimit() {
        when(blacklistCacheService.isBlacklisted(accountId)).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(5L);

        FraudCheckContext ctx = context(new BigDecimal("10000"));
        List<FraudRuleResult> results = fraudRuleChain.evaluate(ctx);

        assertThat(results).allMatch(FraudRuleResult::passed);
        assertThat(fraudRuleChain.hasFailures(results)).isFalse();
    }

    @Test
    void blacklistRule_failsImmediately_andSkipsOtherRules() {
        when(blacklistCacheService.isBlacklisted(accountId)).thenReturn(true);

        FraudCheckContext ctx = context(new BigDecimal("1000"));
        List<FraudRuleResult> results = fraudRuleChain.evaluate(ctx);

        // Only the blacklist rule runs (short-circuit)
        assertThat(results).hasSize(1);
        assertThat(results.get(0).passed()).isFalse();
        assertThat(results.get(0).ruleName()).isEqualTo(BlacklistCheckRule.RULE_NAME);
        assertThat(results.get(0).severity()).isEqualTo(FraudAlertSeverity.CRITICAL);
    }

    @Test
    void velocityRule_failsAtEleventhTransaction_inSameHour() {
        when(blacklistCacheService.isBlacklisted(accountId)).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(11L);

        FraudCheckContext ctx = context(new BigDecimal("100"));
        List<FraudRuleResult> results = fraudRuleChain.evaluate(ctx);

        FraudRuleResult velocityResult = results.stream()
                .filter(r -> VelocityCheckRule.RULE_NAME.equals(r.ruleName()))
                .findFirst().orElseThrow();
        assertThat(velocityResult.passed()).isFalse();
        assertThat(velocityResult.severity()).isEqualTo(FraudAlertSeverity.HIGH);
    }

    @Test
    void largeTransactionRule_failsWhenAmountExceedsThreshold() {
        when(blacklistCacheService.isBlacklisted(accountId)).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(2L);

        FraudCheckContext ctx = context(new BigDecimal("600000"));
        List<FraudRuleResult> results = fraudRuleChain.evaluate(ctx);

        FraudRuleResult largeResult = results.stream()
                .filter(r -> LargeTransactionRule.RULE_NAME.equals(r.ruleName()))
                .findFirst().orElseThrow();
        assertThat(largeResult.passed()).isFalse();
        assertThat(largeResult.severity()).isEqualTo(FraudAlertSeverity.HIGH);
    }

    @Test
    void largeTransactionRule_passesWhenAmountEqualsThreshold() {
        when(blacklistCacheService.isBlacklisted(accountId)).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(1L);

        FraudCheckContext ctx = context(new BigDecimal("500000"));
        List<FraudRuleResult> results = fraudRuleChain.evaluate(ctx);

        FraudRuleResult largeResult = results.stream()
                .filter(r -> LargeTransactionRule.RULE_NAME.equals(r.ruleName()))
                .findFirst().orElseThrow();
        assertThat(largeResult.passed()).isTrue();
    }

    @Test
    void multipleRulesFail_whenVelocityAndLargeTransaction_bothTriggered() {
        when(blacklistCacheService.isBlacklisted(accountId)).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(15L);

        FraudCheckContext ctx = context(new BigDecimal("750000"));
        List<FraudRuleResult> results = fraudRuleChain.evaluate(ctx);

        assertThat(fraudRuleChain.hasFailures(results)).isTrue();
        long failCount = results.stream().filter(r -> !r.passed()).count();
        assertThat(failCount).isEqualTo(2);
    }

    private FraudCheckContext context(BigDecimal amount) {
        return new FraudCheckContext(transactionId, accountId, UUID.randomUUID(),
                amount, "API", Instant.now());
    }
}
