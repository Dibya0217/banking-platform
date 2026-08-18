package com.banking.transaction.service;

import com.banking.transaction.entity.Transaction;
import com.banking.transaction.entity.TransactionStatus;
import com.banking.transaction.entity.TransactionType;
import com.banking.transaction.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaCompensationTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountServiceClient accountServiceClient;
    @Mock private TransactionEventPublisher eventPublisher;
    @Mock private Acknowledgment ack;

    private AccountEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new AccountEventConsumer(transactionRepository, accountServiceClient, eventPublisher, objectMapper);
    }

    @Test
    void creditFailure_duringDebitedSaga_shouldMarkTransactionFailed() {
        UUID txnId = UUID.randomUUID();
        UUID fromAccount = UUID.randomUUID();
        UUID toAccount = UUID.randomUUID();

        Transaction txn = Transaction.builder()
                .id(txnId)
                .fromAccountId(fromAccount)
                .toAccountId(toAccount)
                .transactionType(TransactionType.TRANSFER)
                .amount(new BigDecimal("5000.00"))
                .status(TransactionStatus.PENDING)
                .initiatedBy(UUID.randomUUID())
                .build();

        given(transactionRepository.findById(txnId)).willReturn(Optional.of(txn));
        given(transactionRepository.save(any())).willReturn(txn);
        willThrow(WebClientResponseException.create(503, "Service Unavailable", null, null, null))
                .given(accountServiceClient).credit(any(), any(), any());

        String event = """
                {"eventType":"account.debited","transactionId":"%s"}
                """.formatted(txnId);

        consumer.consume(event, ack);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeast(2)).save(captor.capture());
        // Last saved state should be FAILED
        Transaction lastSaved = captor.getAllValues().getLast();
        assertThat(lastSaved.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(lastSaved.getFailureReason()).contains("Credit failed");
        verify(eventPublisher).publishFailed(any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void accountDebitedEvent_withNoMatchingTransaction_shouldAckGracefully() {
        UUID txnId = UUID.randomUUID();
        given(transactionRepository.findById(txnId)).willReturn(Optional.empty());

        String event = """
                {"eventType":"account.debited","transactionId":"%s"}
                """.formatted(txnId);

        consumer.consume(event, ack);

        verifyNoInteractions(accountServiceClient, eventPublisher);
        verify(ack).acknowledge();
    }

    @Test
    void accountDebitedEvent_forNonTransferTransaction_shouldSkipSaga() {
        UUID txnId = UUID.randomUUID();

        Transaction txn = Transaction.builder()
                .id(txnId)
                .toAccountId(UUID.randomUUID())
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("1000.00"))
                .status(TransactionStatus.COMPLETED)
                .initiatedBy(UUID.randomUUID())
                .build();

        given(transactionRepository.findById(txnId)).willReturn(Optional.of(txn));

        String event = """
                {"eventType":"account.debited","transactionId":"%s"}
                """.formatted(txnId);

        consumer.consume(event, ack);

        verifyNoInteractions(accountServiceClient, eventPublisher);
        verify(ack).acknowledge();
    }
}
