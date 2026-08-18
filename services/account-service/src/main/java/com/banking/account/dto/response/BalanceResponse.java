package com.banking.account.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {

    private UUID accountId;
    private String accountNumber;
    private BigDecimal balance;
    private String currency;
    private boolean cached;
}
