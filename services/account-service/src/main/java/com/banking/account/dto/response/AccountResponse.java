package com.banking.account.dto.response;

import com.banking.account.entity.AccountStatus;
import com.banking.account.entity.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private UUID id;
    private UUID customerId;
    private String accountNumber;
    private AccountType accountType;
    private BigDecimal balance;
    private String currency;
    private AccountStatus status;
    private BigDecimal dailyDebitLimit;
    private String ifscCode;
    private Instant createdAt;
}
