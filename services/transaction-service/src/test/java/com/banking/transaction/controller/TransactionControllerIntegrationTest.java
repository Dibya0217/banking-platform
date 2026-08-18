package com.banking.transaction.controller;

import com.banking.transaction.dto.request.DepositRequest;
import com.banking.transaction.dto.request.TransferRequest;
import com.banking.transaction.dto.request.WithdrawRequest;
import com.banking.transaction.entity.TransactionStatus;
import com.banking.transaction.repository.TransactionRepository;
import com.banking.transaction.service.AccountServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests against Docker Compose PostgreSQL + Redis (ports 5434, 6380).
 * AccountServiceClient is mocked — no account-service required.
 * Kafka provided by @EmbeddedKafka.
 * Run `docker-compose up -d postgres redis` before executing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"banking.transaction.events", "banking.account.events", "banking.fraud.events"})
@DirtiesContext
class TransactionControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TransactionRepository transactionRepository;
    @MockitoBean AccountServiceClient accountServiceClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final UUID USER_ID = UUID.randomUUID();

    // ── deposit ────────────────────────────────────────────────────

    @Test
    void deposit_shouldReturn201AndPersistTransaction() throws Exception {
        doNothing().when(accountServiceClient).credit(any(), any(), any());

        DepositRequest request = new DepositRequest();
        request.setToAccountId(UUID.randomUUID());
        request.setAmount(new BigDecimal("5000.00"));
        request.setIdempotencyKey("test-dep-" + UUID.randomUUID());

        MvcResult result = mockMvc.perform(post("/api/v1/transactions/deposit")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactionType").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID txnId = UUID.fromString(body.at("/data/id").asText());
        assertThat(transactionRepository.findById(txnId)).isPresent();
    }

    @Test
    void deposit_withDuplicateIdempotencyKey_shouldReturn201WithSameData() throws Exception {
        doNothing().when(accountServiceClient).credit(any(), any(), any());

        String idempotencyKey = "idem-dep-" + UUID.randomUUID();
        DepositRequest request = new DepositRequest();
        request.setToAccountId(UUID.randomUUID());
        request.setAmount(new BigDecimal("1000.00"));
        request.setIdempotencyKey(idempotencyKey);

        MvcResult first = mockMvc.perform(post("/api/v1/transactions/deposit")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/transactions/deposit")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).at("/data/id").asText();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).at("/data/id").asText();
        assertThat(firstId).isEqualTo(secondId);
    }

    // ── withdraw ───────────────────────────────────────────────────

    @Test
    void withdraw_shouldReturn201AndPersistTransaction() throws Exception {
        doNothing().when(accountServiceClient).debit(any(), any(), any());

        WithdrawRequest request = new WithdrawRequest();
        request.setFromAccountId(UUID.randomUUID());
        request.setAmount(new BigDecimal("2000.00"));
        request.setIdempotencyKey("test-wdr-" + UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions/withdraw")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transactionType").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    // ── transfer ───────────────────────────────────────────────────

    @Test
    void transfer_shouldReturn202AndStatusPending() throws Exception {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(UUID.randomUUID());
        request.setToAccountId(UUID.randomUUID());
        request.setAmount(new BigDecimal("10000.00"));
        request.setIdempotencyKey("test-tfr-" + UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.transactionType").value("TRANSFER"));
    }

    // ── auth guard ─────────────────────────────────────────────────

    @Test
    void deposit_withoutAuth_shouldReturn403() throws Exception {
        DepositRequest request = new DepositRequest();
        request.setToAccountId(UUID.randomUUID());
        request.setAmount(new BigDecimal("100.00"));
        request.setIdempotencyKey("noauth-dep");

        mockMvc.perform(post("/api/v1/transactions/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
