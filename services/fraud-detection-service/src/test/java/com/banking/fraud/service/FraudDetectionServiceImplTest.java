package com.banking.fraud.service;

import com.banking.fraud.entity.FraudAlert;
import com.banking.fraud.entity.FraudAlertSeverity;
import com.banking.fraud.entity.FraudAlertStatus;
import com.banking.fraud.entity.BlacklistedAccount;
import com.banking.fraud.repository.BlacklistedAccountRepository;
import com.banking.fraud.repository.FraudAlertRepository;
import com.banking.fraud.rule.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceImplTest {

    @Mock private FraudRuleChain fraudRuleChain;
    @Mock private FraudAlertRepository fraudAlertRepository;
    @Mock private BlacklistedAccountRepository blacklistedAccountRepository;
    @Mock private BlacklistCacheService blacklistCacheService;
    @Mock private FraudEventPublisher eventPublisher;

    private FraudDetectionServiceImpl service;

    private final UUID transactionId = UUID.randomUUID();
    private final UUID fromAccountId = UUID.randomUUID();
    private final UUID toAccountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FraudDetectionServiceImpl(fraudRuleChain, fraudAlertRepository,
                blacklistedAccountRepository, blacklistCacheService, eventPublisher);
        ReflectionTestUtils.setField(service, "alertCountThreshold", 3);
        ReflectionTestUtils.setField(service, "alertWindowHours", 24);
    }

    @Test
    void evaluate_allRulesPass_publishesFraudCheckPassed() {
        when(fraudRuleChain.evaluate(any())).thenReturn(List.of(
                FraudRuleResult.pass("BLACKLIST_CHECK"),
                FraudRuleResult.pass("VELOCITY_CHECK"),
                FraudRuleResult.pass("LARGE_TRANSACTION")
        ));

        service.evaluateTransaction(transactionId, fromAccountId, toAccountId, new BigDecimal("1000"), "API");

        verify(eventPublisher).publishFraudCheckPassed(any());
        verify(eventPublisher, never()).publishFraudAlertRaised(any(), any(), anyBoolean());
        verify(fraudAlertRepository, never()).save(any());
    }

    @Test
    void evaluate_velocityFails_persistsAlertAndPublishesFraudAlertRaised() {
        List<FraudRuleResult> results = List.of(
                FraudRuleResult.pass("BLACKLIST_CHECK"),
                FraudRuleResult.fail("VELOCITY_CHECK", "Too many transactions", FraudAlertSeverity.HIGH),
                FraudRuleResult.pass("LARGE_TRANSACTION")
        );
        when(fraudRuleChain.evaluate(any())).thenReturn(results);
        when(fraudAlertRepository.countByFromAccountIdAndSeverityInAndCreatedAtAfter(
                any(), any(), any())).thenReturn(1L);
        when(fraudAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.evaluateTransaction(transactionId, fromAccountId, toAccountId, new BigDecimal("500"), "API");

        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(fraudAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getRuleName()).isEqualTo("VELOCITY_CHECK");
        assertThat(captor.getValue().getSeverity()).isEqualTo(FraudAlertSeverity.HIGH);

        verify(eventPublisher).publishFraudAlertRaised(any(), any(), eq(false));
        verify(eventPublisher, never()).publishFraudCheckPassed(any());
    }

    @Test
    void evaluate_criticalBlacklistHit_publishesWithShouldBlockTrue() {
        List<FraudRuleResult> results = List.of(
                FraudRuleResult.fail("BLACKLIST_CHECK", "Account is blacklisted", FraudAlertSeverity.CRITICAL)
        );
        when(fraudRuleChain.evaluate(any())).thenReturn(results);
        when(fraudAlertRepository.countByFromAccountIdAndSeverityInAndCreatedAtAfter(
                any(), any(), any())).thenReturn(0L);
        when(fraudAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.evaluateTransaction(transactionId, fromAccountId, toAccountId, new BigDecimal("200"), "API");

        verify(eventPublisher).publishFraudAlertRaised(any(), any(), eq(true));
    }

    @Test
    void evaluate_moreThanThresholdHighAlerts_triggersAutoFreeze() {
        List<FraudRuleResult> results = List.of(
                FraudRuleResult.pass("BLACKLIST_CHECK"),
                FraudRuleResult.fail("VELOCITY_CHECK", "Too many transactions", FraudAlertSeverity.HIGH),
                FraudRuleResult.pass("LARGE_TRANSACTION")
        );
        when(fraudRuleChain.evaluate(any())).thenReturn(results);
        when(fraudAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        // Simulate 4 HIGH/CRITICAL alerts in window (> threshold of 3)
        when(fraudAlertRepository.countByFromAccountIdAndSeverityInAndCreatedAtAfter(
                eq(fromAccountId), any(), any())).thenReturn(4L);

        service.evaluateTransaction(transactionId, fromAccountId, toAccountId, new BigDecimal("500"), "API");

        verify(eventPublisher).publishAccountFrozen(eq(fromAccountId), anyString());
    }

    @Test
    void blacklistAccount_savesAndUpdatesCache() {
        when(blacklistedAccountRepository.existsByAccountId(fromAccountId)).thenReturn(false);
        when(blacklistedAccountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.blacklistAccount(fromAccountId, "Suspicious activity", UUID.randomUUID());

        ArgumentCaptor<BlacklistedAccount> captor = ArgumentCaptor.forClass(BlacklistedAccount.class);
        verify(blacklistedAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getAccountId()).isEqualTo(fromAccountId);
        verify(blacklistCacheService).addToBlacklist(fromAccountId);
    }

    @Test
    void blacklistAccount_doesNotDuplicate_ifAlreadyBlacklisted() {
        when(blacklistedAccountRepository.existsByAccountId(fromAccountId)).thenReturn(true);

        service.blacklistAccount(fromAccountId, "Duplicate", UUID.randomUUID());

        verify(blacklistedAccountRepository, never()).save(any());
        verify(blacklistCacheService, never()).addToBlacklist(any());
    }
}
