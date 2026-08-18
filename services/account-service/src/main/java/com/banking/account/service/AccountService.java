package com.banking.account.service;

import com.banking.account.dto.request.CreateAccountRequest;
import com.banking.account.dto.response.AccountResponse;
import com.banking.account.dto.response.BalanceResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AccountService {

    AccountResponse createAccount(CreateAccountRequest request);

    AccountResponse getById(UUID accountId);

    List<AccountResponse> getByCustomerId(UUID customerId);

    BalanceResponse getBalance(UUID accountId);

    void debitAccount(UUID accountId, BigDecimal amount, String transactionId);

    void creditAccount(UUID accountId, BigDecimal amount, String transactionId);

    void freezeAccount(UUID accountId, String reason);

    void closeAccount(UUID accountId);
}
