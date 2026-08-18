package com.banking.account.controller;

import com.banking.account.dto.request.CreateAccountRequest;
import com.banking.account.entity.AccountType;
import com.banking.account.repository.AccountRepository;
import com.banking.account.repository.OutboxRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests against Docker Compose PostgreSQL + Redis (ports 5434, 6380).
 * Kafka provided by @EmbeddedKafka.
 * Run `docker-compose up -d postgres redis` before executing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"banking.account.events", "banking.customer.events"})
@DirtiesContext
class AccountControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccountRepository accountRepository;
    @Autowired OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    // ── createAccount ──────────────────────────────────────────────

    @Test
    void createAccount_shouldReturn201AndPersistRecord() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(CUSTOMER_ID, AccountType.SAVINGS);

        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", CUSTOMER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountNumber").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID accountId = UUID.fromString(body.at("/data/id").asText());

        assertThat(accountRepository.findById(accountId)).isPresent();
    }

    @Test
    void createAccount_shouldPublishAccountCreatedEventToOutbox() throws Exception {
        UUID customerId = UUID.randomUUID();
        CreateAccountRequest request = new CreateAccountRequest(customerId, AccountType.SAVINGS);

        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", customerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID accountId = UUID.fromString(body.at("/data/id").asText());

        assertThat(outboxRepository.findAll())
                .anyMatch(e -> e.getAggregateId().equals(accountId)
                        && e.getEventType().equals("account.created"));
    }

    @Test
    void createAccount_whenMaxAccountsReached_shouldReturn422() throws Exception {
        UUID customerId = UUID.randomUUID();
        CreateAccountRequest request = new CreateAccountRequest(customerId, AccountType.SAVINGS);

        // Create 3 accounts
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/accounts")
                            .header("X-User-Id", customerId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        // 4th should be rejected
        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", customerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MAX_ACCOUNTS_REACHED"));
    }

    // ── getBalance ─────────────────────────────────────────────────

    @Test
    void getBalance_shouldReturnCachedValueOnSecondCall() throws Exception {
        UUID customerId = UUID.randomUUID();
        CreateAccountRequest request = new CreateAccountRequest(customerId, AccountType.SAVINGS);

        MvcResult created = mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", customerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID accountId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).at("/data/id").asText());

        // First call — DB hit, cached=false
        mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId)
                        .header("X-User-Id", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cached").value(false));

        // Second call — Redis hit, cached=true
        mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId)
                        .header("X-User-Id", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cached").value(true));
    }

    // ── freeze ─────────────────────────────────────────────────────

    @Test
    void freezeAccount_withAdminRole_shouldReturn200() throws Exception {
        UUID customerId = UUID.randomUUID();
        CreateAccountRequest request = new CreateAccountRequest(customerId, AccountType.SAVINGS);

        MvcResult created = mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", customerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID accountId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).at("/data/id").asText());

        mockMvc.perform(post("/api/v1/accounts/{id}/freeze", accountId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Fraud detected\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void freezeAccount_withoutAdminRole_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{id}/freeze", UUID.randomUUID())
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
    }
}
