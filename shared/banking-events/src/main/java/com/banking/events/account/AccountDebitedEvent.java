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
public class AccountDebitedEvent extends BaseEvent {

    private String accountId;
    private String customerId;
    private String transactionId;
    private BigDecimal amount;
    private String currency;
    private BigDecimal previousBalance;
    private BigDecimal newBalance;
    private Instant debitedAt;

    public static AccountDebitedEvent of(String accountId, String customerId, String transactionId,
                                          BigDecimal amount, BigDecimal previousBalance,
                                          BigDecimal newBalance, String correlationId) {
        return AccountDebitedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("account.debited")
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
                .debitedAt(Instant.now())
                .build();
    }
}
