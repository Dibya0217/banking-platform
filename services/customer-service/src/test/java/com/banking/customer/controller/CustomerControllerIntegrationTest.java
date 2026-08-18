package com.banking.customer.controller;

import com.banking.customer.config.AuthServiceClient;
import com.banking.customer.dto.request.CustomerRegistrationRequest;
import com.banking.customer.entity.CustomerStatus;
import com.banking.customer.repository.CustomerRepository;
import com.banking.customer.repository.OutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests against Docker Compose PostgreSQL (port 5434).
 * Kafka is provided by @EmbeddedKafka.
 * Run `docker-compose up -d postgres` before executing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"banking.customer.events"})
@DirtiesContext
class CustomerControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired CustomerRepository customerRepository;
    @Autowired OutboxRepository outboxRepository;
    @MockitoBean AuthServiceClient authServiceClient;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final String UNIQUE_EMAIL  = "integration+" + UUID.randomUUID() + "@banking.com";
    private static final String UNIQUE_MOBILE = "9" + (100000000 + (int)(Math.random() * 900000000));

    @BeforeEach
    void setup() {
        doNothing().when(authServiceClient).createCredential(any(), any(), any(), any());
    }

    @Test
    void register_withValidRequest_shouldReturn201AndPersistCustomer() throws Exception {
        CustomerRegistrationRequest request = CustomerRegistrationRequest.builder()
                .fullName("Priya Sharma")
                .email(UNIQUE_EMAIL)
                .mobile(UNIQUE_MOBILE)
                .password("Test@1234")
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customerId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("PENDING_VERIFICATION"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID customerId = UUID.fromString(body.at("/data/customerId").asText());

        assertThat(customerRepository.findById(customerId)).isPresent()
                .hasValueSatisfying(c -> {
                    assertThat(c.getEmail()).isEqualTo(UNIQUE_EMAIL);
                    assertThat(c.getStatus()).isEqualTo(CustomerStatus.PENDING_VERIFICATION);
                });

        assertThat(outboxRepository.findAll())
                .anyMatch(e -> e.getAggregateId().equals(customerId)
                        && e.getEventType().equals("customer.registered"));
    }

    @Test
    void register_withDuplicateEmail_shouldReturn409() throws Exception {
        CustomerRegistrationRequest request = CustomerRegistrationRequest.builder()
                .fullName("Priya Sharma")
                .email(UNIQUE_EMAIL)
                .mobile(UNIQUE_MOBILE)
                .password("Test@1234")
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .build();

        mockMvc.perform(post("/api/v1/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        String duplicateMobile = "9" + (100000000 + (int)(Math.random() * 900000000));
        CustomerRegistrationRequest duplicate = CustomerRegistrationRequest.builder()
                .fullName("Another Person")
                .email(UNIQUE_EMAIL)
                .mobile(duplicateMobile)
                .password("Test@1234")
                .dateOfBirth(LocalDate.of(1992, 1, 1))
                .build();

        mockMvc.perform(post("/api/v1/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_withUnderAgedApplicant_shouldReturn400() throws Exception {
        CustomerRegistrationRequest request = CustomerRegistrationRequest.builder()
                .fullName("Young Person")
                .email("young+" + UUID.randomUUID() + "@test.com")
                .mobile("9" + (100000000 + (int)(Math.random() * 900000000)))
                .password("Test@1234")
                .dateOfBirth(LocalDate.now().minusYears(17))
                .build();

        mockMvc.perform(post("/api/v1/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_withInvalidMobile_shouldReturn400() throws Exception {
        CustomerRegistrationRequest request = CustomerRegistrationRequest.builder()
                .fullName("Priya Sharma")
                .email("valid+" + UUID.randomUUID() + "@test.com")
                .mobile("1234567890")
                .password("Test@1234")
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .build();

        mockMvc.perform(post("/api/v1/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyMobile_shouldUpdateStatusToPendingKyc() throws Exception {
        CustomerRegistrationRequest request = CustomerRegistrationRequest.builder()
                .fullName("Raj Kumar")
                .email("raj+" + UUID.randomUUID() + "@test.com")
                .mobile("9" + (100000000 + (int)(Math.random() * 900000000)))
                .password("Test@1234")
                .dateOfBirth(LocalDate.of(1988, 3, 20))
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID customerId = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .at("/data/customerId").asText());

        mockMvc.perform(post("/api/v1/customers/{id}/verify-mobile", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(customerRepository.findById(customerId))
                .hasValueSatisfying(c -> assertThat(c.getStatus()).isEqualTo(CustomerStatus.PENDING_KYC));
    }
}
