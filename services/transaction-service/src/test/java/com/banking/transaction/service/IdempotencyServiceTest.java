package com.banking.transaction.service;

import com.banking.transaction.dto.response.TransactionResponse;
import com.banking.transaction.entity.TransactionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private IdempotencyService idempotencyService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        idempotencyService = new IdempotencyService(redisTemplate, objectMapper);
        // inject TTL via reflection since @Value isn't wired in unit test
        org.springframework.test.util.ReflectionTestUtils.setField(idempotencyService, "ttlSeconds", 86400L);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    @Test
    void findExisting_whenKeyAbsent_shouldReturnEmpty() {
        given(valueOps.get("idempotency:key-missing")).willReturn(null);

        Optional<TransactionResponse> result = idempotencyService.findExisting("key-missing", TransactionResponse.class);

        assertThat(result).isEmpty();
    }

    @Test
    void findExisting_whenKeyPresent_shouldDeserializeAndReturn() throws Exception {
        UUID id = UUID.randomUUID();
        TransactionResponse response = TransactionResponse.builder()
                .id(id)
                .status(TransactionStatus.COMPLETED)
                .build();
        String json = objectMapper.writeValueAsString(response);
        given(valueOps.get("idempotency:key-exists")).willReturn(json);

        Optional<TransactionResponse> result = idempotencyService.findExisting("key-exists", TransactionResponse.class);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
        assertThat(result.get().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void findExisting_whenMalformedJson_shouldReturnEmpty() {
        given(valueOps.get("idempotency:bad-json")).willReturn("not-valid-json{{");

        Optional<TransactionResponse> result = idempotencyService.findExisting("bad-json", TransactionResponse.class);

        assertThat(result).isEmpty();
    }

    @Test
    void store_shouldSerializeAndSetWithTtl() throws Exception {
        UUID id = UUID.randomUUID();
        TransactionResponse response = TransactionResponse.builder().id(id).build();

        idempotencyService.store("store-key", response);

        verify(valueOps).set(eq("idempotency:store-key"), contains(id.toString()), any(java.time.Duration.class));
    }
}
