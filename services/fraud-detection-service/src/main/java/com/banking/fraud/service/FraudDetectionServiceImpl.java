package com.banking.fraud.service;

import com.banking.fraud.dto.response.FraudAlertResponse;
import com.banking.fraud.entity.*;
import com.banking.fraud.repository.BlacklistedAccountRepository;
import com.banking.fraud.repository.FraudAlertRepository;
import com.banking.fraud.rule.FraudCheckContext;
import com.banking.fraud.rule.FraudRuleChain;
import com.banking.fraud.rule.FraudRuleResult;
import com.banking.common.exception.EntityNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class FraudDetectionServiceImpl implements FraudDetectionService {

    private final FraudRuleChain fraudRuleChain;
    private final FraudAlertRepository fraudAlertRepository;
    private final BlacklistedAccountRepository blacklistedAccountRepository;
    private final BlacklistCacheService blacklistCacheService;
    private final FraudEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    public FraudDetectionServiceImpl(FraudRuleChain fraudRuleChain,
                                     FraudAlertRepository fraudAlertRepository,
                                     BlacklistedAccountRepository blacklistedAccountRepository,
                                     BlacklistCacheService blacklistCacheService,
                                     FraudEventPublisher eventPublisher,
                                     MeterRegistry meterRegistry) {
        this.fraudRuleChain = fraudRuleChain;
        this.fraudAlertRepository = fraudAlertRepository;
        this.blacklistedAccountRepository = blacklistedAccountRepository;
        this.blacklistCacheService = blacklistCacheService;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
    }

    private Counter fraudAlertCounter(String severity, String result) {
        return Counter.builder("banking_fraud_alerts_total")
                .tag("severity", severity)
                .tag("result", result)
                .description("Total number of fraud alerts raised")
                .register(meterRegistry);
    }

    @Value("${fraud.auto-freeze.alert-count-threshold:3}")
    private int alertCountThreshold;

    @Value("${fraud.auto-freeze.alert-window-hours:24}")
    private int alertWindowHours;

    @Override
    @Transactional
    public void evaluateTransaction(UUID transactionId, UUID fromAccountId, UUID toAccountId,
                                    BigDecimal amount, String channel) {
        FraudCheckContext context = new FraudCheckContext(
                transactionId, fromAccountId, toAccountId, amount, channel, Instant.now());

        List<FraudRuleResult> results = fraudRuleChain.evaluate(context);
        List<FraudRuleResult> failures = results.stream().filter(r -> !r.passed()).toList();

        if (failures.isEmpty()) {
            fraudAlertCounter("PASSED", "PASSED").increment();
            eventPublisher.publishFraudCheckPassed(context);
            log.debug("Fraud check passed for transactionId={}", transactionId);
            return;
        }

        // Persist one alert per failed rule
        for (FraudRuleResult failure : failures) {
            FraudAlert alert = FraudAlert.builder()
                    .transactionId(transactionId)
                    .fromAccountId(fromAccountId)
                    .toAccountId(toAccountId)
                    .amount(amount)
                    .ruleName(failure.ruleName())
                    .severity(failure.severity())
                    .reason(failure.reason())
                    .build();
            fraudAlertRepository.save(alert);
        }

        // Block on CRITICAL severity (blacklist hit)
        boolean shouldBlock = failures.stream()
                .anyMatch(r -> r.severity() == FraudAlertSeverity.CRITICAL);

        // Record metrics per failed rule
        for (FraudRuleResult failure : failures) {
            String severityTag = failure.severity().name();
            String resultTag = shouldBlock ? "BLOCKED" : "FLAGGED";
            fraudAlertCounter(severityTag, resultTag).increment();
        }

        eventPublisher.publishFraudAlertRaised(context, failures, shouldBlock);

        // Auto-freeze check: > threshold HIGH/CRITICAL alerts in window
        checkAndAutoFreeze(fromAccountId);
    }

    @Override
    public Page<FraudAlertResponse> getAlerts(FraudAlertStatus status, Pageable pageable) {
        Page<FraudAlert> alerts = status != null
                ? fraudAlertRepository.findByStatus(status, pageable)
                : fraudAlertRepository.findAll(pageable);
        return alerts.map(this::toResponse);
    }

    @Override
    @Transactional
    public FraudAlertResponse resolveAlert(UUID alertId, UUID resolvedBy, String note) {
        FraudAlert alert = fraudAlertRepository.findById(alertId)
                .orElseThrow(() -> new EntityNotFoundException("FraudAlert", alertId.toString()));
        alert.setStatus(FraudAlertStatus.RESOLVED);
        alert.setResolvedBy(resolvedBy);
        alert.setResolvedAt(Instant.now());
        alert.setResolutionNote(note);
        return toResponse(fraudAlertRepository.save(alert));
    }

    @Override
    @Transactional
    public void blacklistAccount(UUID accountId, String reason, UUID blacklistedBy) {
        if (!blacklistedAccountRepository.existsByAccountId(accountId)) {
            BlacklistedAccount entry = BlacklistedAccount.builder()
                    .accountId(accountId)
                    .reason(reason)
                    .blacklistedBy(blacklistedBy)
                    .build();
            blacklistedAccountRepository.save(entry);
            blacklistCacheService.addToBlacklist(accountId);
            log.info("Account {} blacklisted by {}", accountId, blacklistedBy);
        }
    }

    private void checkAndAutoFreeze(UUID fromAccountId) {
        Instant windowStart = Instant.now().minus(alertWindowHours, ChronoUnit.HOURS);
        long criticalHighCount = fraudAlertRepository.countByFromAccountIdAndSeverityInAndCreatedAtAfter(
                fromAccountId,
                List.of(FraudAlertSeverity.HIGH, FraudAlertSeverity.CRITICAL),
                windowStart);

        if (criticalHighCount > alertCountThreshold) {
            String reason = criticalHighCount + " HIGH/CRITICAL fraud alerts in " + alertWindowHours + " hours";
            eventPublisher.publishAccountFrozen(fromAccountId, reason);
            log.warn("Auto-freeze triggered for accountId={}: {}", fromAccountId, reason);
        }
    }

    private FraudAlertResponse toResponse(FraudAlert alert) {
        return FraudAlertResponse.builder()
                .id(alert.getId())
                .transactionId(alert.getTransactionId())
                .fromAccountId(alert.getFromAccountId())
                .toAccountId(alert.getToAccountId())
                .amount(alert.getAmount())
                .ruleName(alert.getRuleName())
                .severity(alert.getSeverity())
                .status(alert.getStatus())
                .reason(alert.getReason())
                .createdAt(alert.getCreatedAt())
                .resolvedAt(alert.getResolvedAt())
                .build();
    }
}
