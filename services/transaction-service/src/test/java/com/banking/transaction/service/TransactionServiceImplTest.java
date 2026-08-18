package com.banking.transaction.service;

import com.banking.transaction.dto.request.DepositRequest;
import com.banking.transaction.dto.request.TransferRequest;
import com.banking.transaction.dto.request.WithdrawRequest;
import com.banking.transaction.dto.response.TransactionResponse;
import com.banking.transaction.entity.Transaction;
import com.banking.transaction.entity.TransactionStatus;
import com.banking.transaction.entity.TransactionType;
import com.banking.transaction.mapper.TransactionMapper;
import com.banking.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private TransactionMapper transactionMapper;
    @Mock private IdempotencyService idempotencyService;
    @Mock private AccountServiceClient accountServiceClient;
    @Mock private TransactionEventPublisher eventPublisher;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private UUID initiatedBy;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        initiatedBy = UUID.randomUUID();
        accountId = UUID.randomUUID();
    }

    // ── deposit ────────────────────────────────────────────────────

    @Test
    void deposit_withNewIdempotencyKey_shouldCreditAndReturnCompleted() {
        DepositRequest request = new DepositRequest();
        request.setToAccountId(accountId);
        request.setAmount(new BigDecimal("5000.00"));
        request.setIdempotencyKey("dep-001");
        request.setChannel("API");

        given(idempotencyService.findExisting("dep-001", TransactionResponse.class))
                .willReturn(Optional.empty());

        Transaction savedTxn = Transaction.builder()
                .id(UUID.randomUUID())
                .toAccountId(accountId)
                .transactionType(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .status(TransactionStatus.PENDING)
                .initiatedBy(initiatedBy)
                .channel("API")
                .build();

        given(transactionRepository.save(any())).willReturn(savedTxn);
        given(transactionMapper.toResponse(any())).willReturn(TransactionResponse.builder().build());

        TransactionResponse response = transactionService.deposit(request, initiatedBy);

        assertThat(response).isNotNull();
        verify(accountServiceClient).credit(eq(accountId), eq(request.getAmount()), any());
        verify(eventPublisher).publishCompleted(any());
        verify(idempotencyService).store(eq("dep-001"), any());
    }

    @Test
    void deposit_withDuplicateIdempotencyKey_shouldReturnCachedResponse() {
        DepositRequest request = new DepositRequest();
        request.setToAccountId(accountId);
        request.setAmount(new BigDecimal("1000.00"));
        request.setIdempotencyKey("dep-dup");

        TransactionResponse cached = TransactionResponse.builder().build();
        given(idempotencyService.findExisting("dep-dup", TransactionResponse.class))
                .willReturn(Optional.of(cached));

        TransactionResponse response = transactionService.deposit(request, initiatedBy);

        assertThat(response).isSameAs(cached);
        verifyNoInteractions(accountServiceClient, transactionRepository, eventPublisher);
    }

    // ── withdraw ───────────────────────────────────────────────────

    @Test
    void withdraw_withNewIdempotencyKey_shouldDebitAndReturnCompleted() {
        WithdrawRequest request = new WithdrawRequest();
        request.setFromAccountId(accountId);
        request.setAmount(new BigDecimal("2000.00"));
        request.setIdempotencyKey("wdr-001");
        request.setChannel("API");

        given(idempotencyService.findExisting("wdr-001", TransactionResponse.class))
                .willReturn(Optional.empty());

        Transaction savedTxn = Transaction.builder()
                .id(UUID.randomUUID())
                .fromAccountId(accountId)
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .status(TransactionStatus.PENDING)
                .initiatedBy(initiatedBy)
                .channel("API")
                .build();

        given(transactionRepository.save(any())).willReturn(savedTxn);
        given(transactionMapper.toResponse(any())).willReturn(TransactionResponse.builder().build());

        TransactionResponse response = transactionService.withdraw(request, initiatedBy);

        assertThat(response).isNotNull();
        verify(accountServiceClient).debit(eq(accountId), eq(request.getAmount()), any());
        verify(eventPublisher).publishCompleted(any());
        verify(idempotencyService).store(eq("wdr-001"), any());
    }

    @Test
    void withdraw_withDuplicateIdempotencyKey_shouldReturnCachedResponse() {
        WithdrawRequest request = new WithdrawRequest();
        request.setFromAccountId(accountId);
        request.setAmount(new BigDecimal("500.00"));
        request.setIdempotencyKey("wdr-dup");

        TransactionResponse cached = TransactionResponse.builder().build();
        given(idempotencyService.findExisting("wdr-dup", TransactionResponse.class))
                .willReturn(Optional.of(cached));

        TransactionResponse response = transactionService.withdraw(request, initiatedBy);

        assertThat(response).isSameAs(cached);
        verifyNoInteractions(accountServiceClient, transactionRepository, eventPublisher);
    }

    // ── transfer ───────────────────────────────────────────────────

    @Test
    void transfer_withValidAccounts_shouldReturnPendingAnd202() {
        UUID fromAccount = UUID.randomUUID();
        UUID toAccount = UUID.randomUUID();

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(fromAccount);
        request.setToAccountId(toAccount);
        request.setAmount(new BigDecimal("10000.00"));
        request.setIdempotencyKey("tfr-001");
        request.setChannel("API");

        given(idempotencyService.findExisting("tfr-001", TransactionResponse.class))
                .willReturn(Optional.empty());

        Transaction savedTxn = Transaction.builder()
                .id(UUID.randomUUID())
                .fromAccountId(fromAccount)
                .toAccountId(toAccount)
                .transactionType(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .status(TransactionStatus.PENDING)
                .initiatedBy(initiatedBy)
                .channel("API")
                .build();

        given(transactionRepository.save(any())).willReturn(savedTxn);
        given(transactionMapper.toResponse(any())).willReturn(
                TransactionResponse.builder().status(TransactionStatus.PENDING).build());

        TransactionResponse response = transactionService.transfer(request, initiatedBy);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING);
        verify(eventPublisher).publishInitiated(any());
        verifyNoMoreInteractions(accountServiceClient);
    }

    @Test
    void transfer_toSameAccount_shouldThrowBusinessRuleException() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(accountId);
        request.setToAccountId(accountId);
        request.setAmount(new BigDecimal("100.00"));
        request.setIdempotencyKey("tfr-self");

        given(idempotencyService.findExisting("tfr-self", TransactionResponse.class))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.transfer(request, initiatedBy))
                .hasMessageContaining("same account");
    }

    // ── getHistory ─────────────────────────────────────────────────

    @Test
    void getById_withUnknownId_shouldThrowTransactionNotFoundException() {
        UUID txnId = UUID.randomUUID();
        given(transactionRepository.findById(txnId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getById(txnId, initiatedBy, false))
                .hasMessageContaining("Transaction not found");
    }
}
