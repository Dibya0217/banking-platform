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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountEventConsumerTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountServiceClient accountServiceClient;
    @Mock private TransactionEventPublisher eventPublisher;
    @Mock private Acknowledgment ack;

    private AccountEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new AccountEventConsumer(transactionRepository, accountServiceClient, eventPublisher, objectMapper);
    }

    @Test
    void consume_accountDebitedEvent_forTransfer_shouldCreditRecipient() {
        UUID txnId = UUID.randomUUID();
        UUID fromAccount = UUID.randomUUID();
        UUID toAccount = UUID.randomUUID();

        Transaction txn = Transaction.builder()
                .id(txnId)
                .fromAccountId(fromAccount)
                .toAccountId(toAccount)
                .transactionType(TransactionType.TRANSFER)
                .amount(new BigDecimal("1000.00"))
                .status(TransactionStatus.PENDING)
                .initiatedBy(UUID.randomUUID())
                .build();

        given(transactionRepository.findById(txnId)).willReturn(Optional.of(txn));
        given(transactionRepository.save(any())).willReturn(txn);

        String event = """
                {
                  "eventType": "account.debited",
                  "transactionId": "%s"
                }
                """.formatted(txnId);

        consumer.consume(event, ack);

        verify(accountServiceClient).credit(eq(toAccount), eq(new BigDecimal("1000.00")), any());
        verify(ack).acknowledge();
    }

    @Test
    void consume_accountCreditedEvent_forTransfer_shouldMarkCompleted() {
        UUID txnId = UUID.randomUUID();

        Transaction txn = Transaction.builder()
                .id(txnId)
                .fromAccountId(UUID.randomUUID())
                .toAccountId(UUID.randomUUID())
                .transactionType(TransactionType.TRANSFER)
                .amount(new BigDecimal("500.00"))
                .status(TransactionStatus.CREDIT_PENDING)
                .initiatedBy(UUID.randomUUID())
                .build();

        given(transactionRepository.findById(txnId)).willReturn(Optional.of(txn));
        given(transactionRepository.save(any())).willReturn(txn);

        String event = """
                {
                  "eventType": "account.credited",
                  "transactionId": "%s"
                }
                """.formatted(txnId);

        consumer.consume(event, ack);

        assertThat(txn.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verify(eventPublisher).publishCompleted(txn);
        verify(ack).acknowledge();
    }

    @Test
    void consume_unknownEventType_shouldAckWithoutAction() {
        String event = """
                {
                  "eventType": "account.frozen",
                  "transactionId": "%s"
                }
                """.formatted(UUID.randomUUID());

        consumer.consume(event, ack);

        verifyNoInteractions(transactionRepository, accountServiceClient, eventPublisher);
        verify(ack).acknowledge();
    }

    @Test
    void consume_malformedJson_shouldNotAck() {
        consumer.consume("not-json", ack);

        verifyNoInteractions(accountServiceClient, eventPublisher);
        verify(ack, never()).acknowledge();
    }
}
