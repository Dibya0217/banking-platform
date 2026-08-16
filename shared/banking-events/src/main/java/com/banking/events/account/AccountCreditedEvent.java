package com.banking.events.account;

import com.banking.events.BaseEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@SuperBuilder
@NoArgsConstructor
public class AccountCreditedEvent extends BaseEvent {

    private String accountId;
    private String customerId;
    private String transactionId;
    private BigDecimal amount;
    private String currency;
    private BigDecimal previousBalance;
    private BigDecimal newBalance;
    private Instant creditedAt;

    public static AccountCreditedEvent of(String accountId, String customerId, String transactionId,
                                           BigDecimal amount, BigDecimal previousBalance,
                                           BigDecimal newBalance, String correlationId) {
        return AccountCreditedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("account.credited")
                .eventVersion("1.0")
                .producedAt(Instant.now())
                .producerService("account-service")
                .correlationId(correlationId)
                .accountId(accountId)
                .customerId(customerId)
                .transactionId(transactionId)
                .amount(amount)
                .currency("INR")
                .previousBalance(previousBalance)
                .newBalance(newBalance)
                .creditedAt(Instant.now())
                .build();
    }
}
