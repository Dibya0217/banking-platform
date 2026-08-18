package com.banking.account.service;

import com.banking.account.dto.request.CreateAccountRequest;
import com.banking.account.dto.response.AccountResponse;
import com.banking.account.entity.AccountType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerEventConsumerTest {

    @Mock private AccountService accountService;
    @Mock private Acknowledgment ack;

    private CustomerEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CustomerEventConsumer(accountService,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void consume_kycApprovedEvent_shouldAutoCreateSavingsAccount() {
        UUID customerId = UUID.randomUUID();
        String event = """
                {
                  "eventId": "%s",
                  "eventType": "customer.kyc.approved",
                  "customerId": "%s",
                  "kycId": "%s",
                  "approvedBy": "admin-1"
                }
                """.formatted(UUID.randomUUID(), customerId, UUID.randomUUID());

        given(accountService.createAccount(any())).willReturn(new AccountResponse());

        consumer.consume(event, customerId.toString(), ack);

        ArgumentCaptor<CreateAccountRequest> captor = ArgumentCaptor.forClass(CreateAccountRequest.class);
        verify(accountService).createAccount(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isEqualTo(customerId);
        assertThat(captor.getValue().getAccountType()).isEqualTo(AccountType.SAVINGS);
        verify(ack).acknowledge();
    }

    @Test
    void consume_otherEventType_shouldSkipAndAck() {
        UUID customerId = UUID.randomUUID();
        String event = """
                {
                  "eventType": "customer.registered",
                  "customerId": "%s"
                }
                """.formatted(customerId);

        consumer.consume(event, customerId.toString(), ack);

        verify(accountService, never()).createAccount(any());
        verify(ack).acknowledge();
    }

    @Test
    void consume_malformedJson_shouldNotAck() {
        consumer.consume("not-valid-json", "key", ack);

        verify(accountService, never()).createAccount(any());
        verify(ack, never()).acknowledge();
    }
}
