package com.banking.fraud.rule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FraudCheckContext(
        UUID transactionId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String channel,
        Instant initiatedAt
) {}
