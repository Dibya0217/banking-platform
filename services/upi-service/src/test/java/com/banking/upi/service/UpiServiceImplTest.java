package com.banking.upi.service;

import com.banking.common.exception.BusinessRuleException;
import com.banking.upi.dto.request.ChangePinRequest;
import com.banking.upi.dto.request.CreateVpaRequest;
import com.banking.upi.dto.request.UpiTransferRequest;
import com.banking.upi.dto.response.UpiIdResponse;
import com.banking.upi.dto.response.UpiTransactionResponse;
import com.banking.upi.entity.UpiId;
import com.banking.upi.entity.UpiStatus;
import com.banking.upi.entity.UpiTransaction;
import com.banking.upi.mapper.UpiMapper;
import com.banking.upi.repository.UpiIdRepository;
import com.banking.upi.repository.UpiTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpiServiceImplTest {

    @Mock private UpiIdRepository upiIdRepository;
    @Mock private UpiTransactionRepository upiTransactionRepository;
    @Mock private UpiPinEncryptor pinEncryptor;
    @Mock private DailyLimitService dailyLimitService;
    @Mock private TransactionServiceClient transactionServiceClient;
    @Mock private UpiMapper upiMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private UpiServiceImpl upiService;

    private UUID customerId;
    private UUID upiIdId;
    private UpiId activeUpiId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        upiIdId = UUID.randomUUID();
        activeUpiId = UpiId.builder()
                .id(upiIdId)
                .customerId(customerId)
                .accountId(UUID.randomUUID())
                .vpa("priya@bank")
                .pinHash("$2a$10$hashed")
                .dailyLimit(new BigDecimal("100000.00"))
                .status(UpiStatus.ACTIVE)
                .build();
    }

    // ── createVpa ──────────────────────────────────────────────────

    @Test
    void createVpa_withUniqueVpa_shouldSaveAndReturn() {
        CreateVpaRequest request = new CreateVpaRequest();
        request.setAccountId(UUID.randomUUID());
        request.setVpa("priya@bank");
        request.setPin("123456");

        given(upiIdRepository.existsByVpa("priya@bank")).willReturn(false);
        given(pinEncryptor.hash("123456")).willReturn("$2a$10$hashed");
        given(upiIdRepository.save(any())).willReturn(activeUpiId);
        given(upiMapper.toResponse(activeUpiId)).willReturn(UpiIdResponse.builder().build());

        UpiIdResponse response = upiService.createVpa(request, customerId);

        assertThat(response).isNotNull();
        verify(upiIdRepository).save(any(UpiId.class));
    }

    @Test
    void createVpa_withDuplicateVpa_shouldThrowBusinessRuleException() {
        CreateVpaRequest request = new CreateVpaRequest();
        request.setAccountId(UUID.randomUUID());
        request.setVpa("taken@bank");
        request.setPin("123456");

        given(upiIdRepository.existsByVpa("taken@bank")).willReturn(true);

        assertThatThrownBy(() -> upiService.createVpa(request, customerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already taken");
    }

    // ── changePin ──────────────────────────────────────────────────

    @Test
    void changePin_withCorrectCurrentPin_shouldUpdateHash() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);
        given(upiIdRepository.findById(upiIdId)).willReturn(Optional.of(activeUpiId));
        given(pinEncryptor.verify("123456", "$2a$10$hashed")).willReturn(true);
        given(pinEncryptor.hash("654321")).willReturn("$2a$10$newhash");

        ChangePinRequest request = new ChangePinRequest();
        request.setCurrentPin("123456");
        request.setNewPin("654321");

        upiService.changePin(upiIdId, request, customerId);

        assertThat(activeUpiId.getPinHash()).isEqualTo("$2a$10$newhash");
        verify(upiIdRepository).save(activeUpiId);
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void changePin_withWrongCurrentPin_shouldThrowBusinessRuleException() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);
        given(upiIdRepository.findById(upiIdId)).willReturn(Optional.of(activeUpiId));
        given(pinEncryptor.verify("wrongpin", "$2a$10$hashed")).willReturn(false);

        ChangePinRequest request = new ChangePinRequest();
        request.setCurrentPin("wrongpin");
        request.setNewPin("654321");

        assertThatThrownBy(() -> upiService.changePin(upiIdId, request, customerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("incorrect");

        verify(redisTemplate).delete(anyString()); // lock always released
    }

    @Test
    void changePin_whenLockAlreadyHeld_shouldThrowBusinessRuleException() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(false);

        ChangePinRequest request = new ChangePinRequest();
        request.setCurrentPin("123456");
        request.setNewPin("654321");

        assertThatThrownBy(() -> upiService.changePin(upiIdId, request, customerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("in progress");
    }

    // ── transfer ───────────────────────────────────────────────────

    @Test
    void transfer_withValidPinAndWithinLimit_shouldSucceed() {
        UpiId payeeUpi = UpiId.builder()
                .id(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .vpa("rahul@bank")
                .pinHash("hash2")
                .dailyLimit(new BigDecimal("100000.00"))
                .status(UpiStatus.ACTIVE)
                .build();

        UpiTransferRequest request = new UpiTransferRequest();
        request.setPayerVpa("priya@bank");
        request.setPayeeVpa("rahul@bank");
        request.setAmount(new BigDecimal("1000.00"));
        request.setPin("123456");
        request.setIdempotencyKey("upi-001");

        given(upiIdRepository.findByVpa("priya@bank")).willReturn(Optional.of(activeUpiId));
        given(upiIdRepository.findByVpa("rahul@bank")).willReturn(Optional.of(payeeUpi));
        given(pinEncryptor.verify("123456", "$2a$10$hashed")).willReturn(true);
        given(dailyLimitService.getUsedToday("priya@bank")).willReturn(BigDecimal.ZERO);
        given(transactionServiceClient.initiateTransfer(any(), any(), any(), any(), any()))
                .willReturn(UUID.randomUUID());
        given(dailyLimitService.addUsage(any(), any())).willReturn(new BigDecimal("1000.00"));

        UpiTransaction savedTxn = UpiTransaction.builder().id(UUID.randomUUID())
                .payerVpa("priya@bank").payeeVpa("rahul@bank")
                .amount(new BigDecimal("1000.00")).status("PENDING").build();
        given(upiTransactionRepository.save(any())).willReturn(savedTxn);
        given(upiMapper.toResponse(savedTxn)).willReturn(UpiTransactionResponse.builder().build());

        UpiTransactionResponse response = upiService.transfer(request, customerId);

        assertThat(response).isNotNull();
        verify(dailyLimitService).addUsage("priya@bank", new BigDecimal("1000.00"));
    }

    @Test
    void transfer_withIncorrectPin_shouldThrowBusinessRuleException() {
        UpiId payeeUpi = UpiId.builder().id(UUID.randomUUID()).customerId(UUID.randomUUID())
                .vpa("rahul@bank").pinHash("hash2").status(UpiStatus.ACTIVE).build();

        UpiTransferRequest request = new UpiTransferRequest();
        request.setPayerVpa("priya@bank");
        request.setPayeeVpa("rahul@bank");
        request.setAmount(new BigDecimal("500.00"));
        request.setPin("000000");
        request.setIdempotencyKey("upi-002");

        given(upiIdRepository.findByVpa("priya@bank")).willReturn(Optional.of(activeUpiId));
        given(upiIdRepository.findByVpa("rahul@bank")).willReturn(Optional.of(payeeUpi));
        given(pinEncryptor.verify("000000", "$2a$10$hashed")).willReturn(false);

        assertThatThrownBy(() -> upiService.transfer(request, customerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PIN is incorrect");
    }

    @Test
    void transfer_whenDailyLimitExceeded_shouldThrowBusinessRuleException() {
        UpiId payeeUpi = UpiId.builder().id(UUID.randomUUID()).customerId(UUID.randomUUID())
                .vpa("rahul@bank").pinHash("hash2").status(UpiStatus.ACTIVE).build();

        UpiTransferRequest request = new UpiTransferRequest();
        request.setPayerVpa("priya@bank");
        request.setPayeeVpa("rahul@bank");
        request.setAmount(new BigDecimal("60000.00"));
        request.setPin("123456");
        request.setIdempotencyKey("upi-003");

        given(upiIdRepository.findByVpa("priya@bank")).willReturn(Optional.of(activeUpiId));
        given(upiIdRepository.findByVpa("rahul@bank")).willReturn(Optional.of(payeeUpi));
        given(pinEncryptor.verify("123456", "$2a$10$hashed")).willReturn(true);
        given(dailyLimitService.getUsedToday("priya@bank")).willReturn(new BigDecimal("50000.00"));

        assertThatThrownBy(() -> upiService.transfer(request, customerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Daily limit");
    }

    @Test
    void transfer_toSameVpa_shouldThrowBusinessRuleException() {
        UpiTransferRequest request = new UpiTransferRequest();
        request.setPayerVpa("priya@bank");
        request.setPayeeVpa("priya@bank");
        request.setAmount(new BigDecimal("100.00"));
        request.setPin("123456");
        request.setIdempotencyKey("upi-self");

        assertThatThrownBy(() -> upiService.transfer(request, customerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("same VPA");
    }
}
