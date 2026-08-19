package com.banking.fraud.dto.response;

import com.banking.fraud.entity.FraudAlertSeverity;
import com.banking.fraud.entity.FraudAlertStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class FraudAlertResponse {
    private UUID id;
    private UUID transactionId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private BigDecimal amount;
    private String ruleName;
    private FraudAlertSeverity severity;
    private FraudAlertStatus status;
    private String reason;
    private Instant createdAt;
    private Instant resolvedAt;
}
