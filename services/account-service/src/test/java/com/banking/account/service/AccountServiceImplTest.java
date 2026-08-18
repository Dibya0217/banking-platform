package com.banking.account.service;

import com.banking.account.dto.request.CreateAccountRequest;
import com.banking.account.dto.response.AccountResponse;
import com.banking.account.dto.response.BalanceResponse;
import com.banking.account.entity.Account;
import com.banking.account.entity.AccountStatus;
import com.banking.account.entity.AccountType;
import com.banking.account.exception.AccountNotFoundException;
import com.banking.account.mapper.AccountMapper;
import com.banking.account.repository.AccountRepository;
import com.banking.common.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private AccountMapper accountMapper;
    @Mock private AccountNumberGenerator accountNumberGenerator;
    @Mock private BalanceCacheService balanceCacheService;
    @Mock private AccountEventPublisher eventPublisher;

    @InjectMocks
    private AccountServiceImpl accountService;

    private UUID accountId;
    private UUID customerId;
    private Account activeAccount;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(accountService, "maxAccountsPerCustomer", 3);
        accountId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        activeAccount = Account.builder()
                .id(accountId)
                .customerId(customerId)
                .accountNumber("202608180001234")
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("10000.00"))
                .dailyDebitLimit(new BigDecimal("100000.00"))
                .status(AccountStatus.ACTIVE)
                .build();
    }

    // ── createAccount ──────────────────────────────────────────────

    @Test
    void createAccount_withValidRequest_shouldSaveAndReturnResponse() {
        CreateAccountRequest request = new CreateAccountRequest(customerId, AccountType.SAVINGS);
        given(accountRepository.countByCustomerId(customerId)).willReturn(0L);
        given(accountNumberGenerator.generate()).willReturn("2026081800000001");
        given(accountRepository.save(any())).willReturn(activeAccount);
        given(accountMapper.toResponse(activeAccount)).willReturn(new AccountResponse());

        AccountResponse response = accountService.createAccount(request);

        assertThat(response).isNotNull();
        verify(accountRepository).save(any(Account.class));
        verify(eventPublisher).publishAccountCreated(any());
    }

    @Test
    void createAccount_whenMaxAccountsReached_shouldThrowBusinessRuleException() {
        CreateAccountRequest request = new CreateAccountRequest(customerId, AccountType.SAVINGS);
        given(accountRepository.countByCustomerId(customerId)).willReturn(3L);

        assertThatThrownBy(() -> accountService.createAccount(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Maximum allowed");
    }

    // ── debit ──────────────────────────────────────────────────────

    @Test
    void debitAccount_withSufficientFunds_shouldReduceBalance() {
        given(accountRepository.findById(accountId)).willReturn(Optional.of(activeAccount));
        given(accountRepository.save(any())).willReturn(activeAccount);

        accountService.debitAccount(accountId, new BigDecimal("3000.00"), UUID.randomUUID().toString());

        assertThat(activeAccount.getBalance()).isEqualByComparingTo("7000.00");
        verify(balanceCacheService).invalidateBalance(accountId);
        verify(eventPublisher).publishAccountDebited(any());
    }

    @Test
    void debitAccount_withInsufficientFunds_shouldThrowBusinessRuleException() {
        given(accountRepository.findById(accountId)).willReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.debitAccount(accountId, new BigDecimal("50000.00"), "txn-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void debitAccount_onFrozenAccount_shouldThrowBusinessRuleException() {
        activeAccount.setStatus(AccountStatus.FROZEN);
        given(accountRepository.findById(accountId)).willReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.debitAccount(accountId, new BigDecimal("100.00"), "txn-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("frozen");
    }

    @Test
    void debitAccount_onClosedAccount_shouldThrowBusinessRuleException() {
        activeAccount.setStatus(AccountStatus.CLOSED);
        given(accountRepository.findById(accountId)).willReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.debitAccount(accountId, new BigDecimal("100.00"), "txn-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("closed");
    }

    // ── credit ─────────────────────────────────────────────────────

    @Test
    void creditAccount_shouldIncreaseBalance() {
        given(accountRepository.findById(accountId)).willReturn(Optional.of(activeAccount));
        given(accountRepository.save(any())).willReturn(activeAccount);

        accountService.creditAccount(accountId, new BigDecimal("5000.00"), UUID.randomUUID().toString());

        assertThat(activeAccount.getBalance()).isEqualByComparingTo("15000.00");
        verify(eventPublisher).publishAccountCredited(any());
    }

    @Test
    void creditAccount_onClosedAccount_shouldThrowBusinessRuleException() {
        activeAccount.setStatus(AccountStatus.CLOSED);
        given(accountRepository.findById(accountId)).willReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.creditAccount(accountId, new BigDecimal("100.00"), "txn-1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("closed");
    }

    // ── getBalance ─────────────────────────────────────────────────

    @Test
    void getBalance_shouldReturnCachedValueOnSecondCall() {
        given(balanceCacheService.getCachedBalance(accountId))
                .willReturn(Optional.of(new BigDecimal("10000.00")));
        given(accountRepository.findById(accountId)).willReturn(Optional.of(activeAccount));

        BalanceResponse response = accountService.getBalance(accountId);

        assertThat(response.isCached()).isTrue();
        assertThat(response.getBalance()).isEqualByComparingTo("10000.00");
        verify(balanceCacheService, never()).cacheBalance(any(), any());
    }

    @Test
    void getBalance_withCacheMiss_shouldQueryDbAndCacheResult() {
        given(balanceCacheService.getCachedBalance(accountId)).willReturn(Optional.empty());
        given(accountRepository.findById(accountId)).willReturn(Optional.of(activeAccount));

        BalanceResponse response = accountService.getBalance(accountId);

        assertThat(response.isCached()).isFalse();
        verify(balanceCacheService).cacheBalance(accountId, activeAccount.getBalance());
    }

    // ── freeze / close ─────────────────────────────────────────────

    @Test
    void freezeAccount_shouldUpdateStatusAndInvalidateCache() {
        given(accountRepository.findById(accountId)).willReturn(Optional.of(activeAccount));

        accountService.freezeAccount(accountId, "Suspicious activity");

        assertThat(activeAccount.getStatus()).isEqualTo(AccountStatus.FROZEN);
        verify(balanceCacheService).invalidateBalance(accountId);
    }

    @Test
    void closeAccount_withZeroBalance_shouldSucceed() {
        activeAccount.setBalance(BigDecimal.ZERO);
        given(accountRepository.findById(accountId)).willReturn(Optional.of(activeAccount));

        accountService.closeAccount(accountId);

        assertThat(activeAccount.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(activeAccount.getClosedAt()).isNotNull();
    }

    @Test
    void closeAccount_withNonZeroBalance_shouldThrowBusinessRuleException() {
        given(accountRepository.findById(accountId)).willReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.closeAccount(accountId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("non-zero balance");
    }

    @Test
    void getById_withUnknownId_shouldThrowAccountNotFoundException() {
        given(accountRepository.findById(accountId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getById(accountId))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
