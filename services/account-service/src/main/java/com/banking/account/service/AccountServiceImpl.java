package com.banking.account.service;

import com.banking.account.dto.request.CreateAccountRequest;
import com.banking.account.dto.response.AccountResponse;
import com.banking.account.dto.response.BalanceResponse;
import com.banking.account.entity.Account;
import com.banking.account.entity.AccountStatus;
import com.banking.account.exception.AccountNotFoundException;
import com.banking.account.mapper.AccountMapper;
import com.banking.account.repository.AccountRepository;
import com.banking.common.exception.BusinessRuleException;
import com.banking.events.account.AccountCreatedEvent;
import com.banking.events.account.AccountCreditedEvent;
import com.banking.events.account.AccountDebitedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private static final int MAX_LOCK_RETRIES = 3;

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final AccountNumberGenerator accountNumberGenerator;
    private final BalanceCacheService balanceCacheService;
    private final AccountEventPublisher eventPublisher;

    @Value("${account.max-per-customer:3}")
    private int maxAccountsPerCustomer;

    @Override
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        long existing = accountRepository.countByCustomerId(request.getCustomerId());
        if (existing >= maxAccountsPerCustomer) {
            throw new BusinessRuleException("MAX_ACCOUNTS_REACHED",
                    "Customer already has " + existing + " accounts. Maximum allowed is " + maxAccountsPerCustomer);
        }

        String accountNumber = accountNumberGenerator.generate();
        Account account = Account.builder()
                .customerId(request.getCustomerId())
                .accountNumber(accountNumber)
                .accountType(request.getAccountType())
                .build();

        account = accountRepository.save(account);
        log.info("Account created: id={}, number={}, customerId={}", account.getId(), accountNumber, request.getCustomerId());

        eventPublisher.publishAccountCreated(AccountCreatedEvent.of(
                account.getId().toString(),
                account.getCustomerId().toString(),
                accountNumber,
                account.getAccountType().name(),
                MDC.get("correlationId")
        ));

        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getById(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getByCustomerId(UUID customerId) {
        return accountRepository.findByCustomerId(customerId).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID accountId) {
        var cached = balanceCacheService.getCachedBalance(accountId);
        if (cached.isPresent()) {
            log.debug("Balance cache HIT for accountId={}", accountId);
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
            return BalanceResponse.builder()
                    .accountId(accountId)
                    .accountNumber(account.getAccountNumber())
                    .balance(cached.get())
                    .currency(account.getCurrency())
                    .cached(true)
                    .build();
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        balanceCacheService.cacheBalance(accountId, account.getBalance());

        return BalanceResponse.builder()
                .accountId(accountId)
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .cached(false)
                .build();
    }

    @Override
    @Transactional
    public void debitAccount(UUID accountId, BigDecimal amount, String transactionId) {
        for (int attempt = 1; attempt <= MAX_LOCK_RETRIES; attempt++) {
            try {
                Account account = accountRepository.findById(accountId)
                        .orElseThrow(() -> new AccountNotFoundException(accountId));

                BigDecimal previousBalance = account.getBalance();
                account.debit(amount);
                accountRepository.save(account);
                balanceCacheService.invalidateBalance(accountId);

                eventPublisher.publishAccountDebited(AccountDebitedEvent.of(
                        accountId.toString(),
                        account.getCustomerId().toString(),
                        transactionId,
                        amount,
                        previousBalance,
                        account.getBalance(),
                        MDC.get("correlationId")
                ));

                log.info("Account debited: id={}, amount={}, txn={}", accountId, amount, transactionId);
                return;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_LOCK_RETRIES) throw e;
                log.warn("Optimistic lock conflict on debit, attempt {}/{}", attempt, MAX_LOCK_RETRIES);
            }
        }
    }

    @Override
    @Transactional
    public void creditAccount(UUID accountId, BigDecimal amount, String transactionId) {
        for (int attempt = 1; attempt <= MAX_LOCK_RETRIES; attempt++) {
            try {
                Account account = accountRepository.findById(accountId)
                        .orElseThrow(() -> new AccountNotFoundException(accountId));

                BigDecimal previousBalance = account.getBalance();
                account.credit(amount);
                accountRepository.save(account);
                balanceCacheService.invalidateBalance(accountId);

                eventPublisher.publishAccountCredited(AccountCreditedEvent.of(
                        accountId.toString(),
                        account.getCustomerId().toString(),
                        transactionId,
                        amount,
                        previousBalance,
                        account.getBalance(),
                        MDC.get("correlationId")
                ));

                log.info("Account credited: id={}, amount={}, txn={}", accountId, amount, transactionId);
                return;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_LOCK_RETRIES) throw e;
                log.warn("Optimistic lock conflict on credit, attempt {}/{}", attempt, MAX_LOCK_RETRIES);
            }
        }
    }

    @Override
    @Transactional
    public void freezeAccount(UUID accountId, String reason) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        account.setStatus(AccountStatus.FROZEN);
        account.setFreezeReason(reason);
        accountRepository.save(account);
        balanceCacheService.invalidateBalance(accountId);
        log.info("Account frozen: id={}, reason={}", accountId, reason);
    }

    @Override
    @Transactional
    public void closeAccount(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessRuleException("NON_ZERO_BALANCE",
                    "Account cannot be closed with non-zero balance: " + account.getBalance());
        }
        account.setStatus(AccountStatus.CLOSED);
        account.setClosedAt(Instant.now());
        accountRepository.save(account);
        balanceCacheService.invalidateBalance(accountId);
        log.info("Account closed: id={}", accountId);
    }
}
