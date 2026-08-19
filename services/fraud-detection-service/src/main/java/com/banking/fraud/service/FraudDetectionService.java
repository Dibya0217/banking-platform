package com.banking.fraud.service;

import com.banking.fraud.dto.response.FraudAlertResponse;
import com.banking.fraud.entity.FraudAlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface FraudDetectionService {

    void evaluateTransaction(UUID transactionId, UUID fromAccountId, UUID toAccountId,
                             BigDecimal amount, String channel);

    Page<FraudAlertResponse> getAlerts(FraudAlertStatus status, Pageable pageable);

    FraudAlertResponse resolveAlert(UUID alertId, UUID resolvedBy, String note);

    void blacklistAccount(UUID accountId, String reason, UUID blacklistedBy);
}
