package com.banking.beneficiary.service;

import com.banking.beneficiary.dto.request.AddBeneficiaryRequest;
import com.banking.beneficiary.dto.response.BeneficiaryResponse;
import com.banking.beneficiary.entity.Beneficiary;
import com.banking.beneficiary.entity.BeneficiaryStatus;
import com.banking.beneficiary.exception.BeneficiaryNotFoundException;
import com.banking.beneficiary.mapper.BeneficiaryMapper;
import com.banking.beneficiary.repository.BeneficiaryRepository;
import com.banking.common.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BeneficiaryServiceImplTest {

    @Mock private BeneficiaryRepository beneficiaryRepository;
    @Mock private BeneficiaryMapper beneficiaryMapper;
    @Mock private PennyDropService pennyDropService;

    @InjectMocks
    private BeneficiaryServiceImpl beneficiaryService;

    private UUID customerId;
    private UUID beneficiaryId;
    private Beneficiary activeBeneficiary;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(beneficiaryService, "maxPerCustomer", 20);
        customerId = UUID.randomUUID();
        beneficiaryId = UUID.randomUUID();
        activeBeneficiary = Beneficiary.builder()
                .id(beneficiaryId)
                .customerId(customerId)
                .accountNumber("123456789012")
                .ifscCode("HDFC0001234")
                .beneficiaryName("John Doe")
                .status(BeneficiaryStatus.ACTIVE)
                .transferEnabledAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
    }

    // ── add ─────────────────────────────────────────────────────────

    @Test
    void add_withValidRequest_shouldSaveAndTriggerPennyDrop() {
        AddBeneficiaryRequest request = buildRequest("987654321012", "SBIN0001234");

        given(beneficiaryRepository.findByCustomerIdAndAccountNumberAndIfscCode(any(), any(), any()))
                .willReturn(Optional.empty());
        given(beneficiaryRepository.countByCustomerIdAndStatusNot(customerId, BeneficiaryStatus.REMOVED))
                .willReturn(5L);
        given(beneficiaryRepository.save(any())).willReturn(activeBeneficiary);
        given(beneficiaryMapper.toResponse(any())).willReturn(BeneficiaryResponse.builder().build());

        BeneficiaryResponse response = beneficiaryService.add(request, customerId);

        assertThat(response).isNotNull();
        verify(beneficiaryRepository).save(any(Beneficiary.class));
        verify(pennyDropService).simulatePennyDrop(any());
    }

    @Test
    void add_whenDuplicateActive_shouldThrowBusinessRuleException() {
        AddBeneficiaryRequest request = buildRequest("123456789012", "HDFC0001234");

        given(beneficiaryRepository.findByCustomerIdAndAccountNumberAndIfscCode(any(), any(), any()))
                .willReturn(Optional.of(activeBeneficiary));

        assertThatThrownBy(() -> beneficiaryService.add(request, customerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void add_whenMaxLimitReached_shouldThrowBusinessRuleException() {
        AddBeneficiaryRequest request = buildRequest("111111111111", "ICIC0001234");

        given(beneficiaryRepository.findByCustomerIdAndAccountNumberAndIfscCode(any(), any(), any()))
                .willReturn(Optional.empty());
        given(beneficiaryRepository.countByCustomerIdAndStatusNot(customerId, BeneficiaryStatus.REMOVED))
                .willReturn(20L);

        assertThatThrownBy(() -> beneficiaryService.add(request, customerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Maximum");
    }

    // ── cooldown ────────────────────────────────────────────────────

    @Test
    void isTransferAllowed_whenCooldownExpired_shouldReturnTrue() {
        given(beneficiaryRepository.findById(beneficiaryId)).willReturn(Optional.of(activeBeneficiary));

        boolean allowed = beneficiaryService.isTransferAllowed(beneficiaryId, customerId);

        assertThat(allowed).isTrue();
    }

    @Test
    void isTransferAllowed_whenCooldownNotExpired_shouldReturnFalse() {
        activeBeneficiary.setTransferEnabledAt(Instant.now().plus(23, ChronoUnit.HOURS));
        given(beneficiaryRepository.findById(beneficiaryId)).willReturn(Optional.of(activeBeneficiary));

        boolean allowed = beneficiaryService.isTransferAllowed(beneficiaryId, customerId);

        assertThat(allowed).isFalse();
    }

    @Test
    void isTransferAllowed_whenPendingVerification_shouldReturnFalse() {
        activeBeneficiary.setStatus(BeneficiaryStatus.PENDING_VERIFICATION);
        given(beneficiaryRepository.findById(beneficiaryId)).willReturn(Optional.of(activeBeneficiary));

        boolean allowed = beneficiaryService.isTransferAllowed(beneficiaryId, customerId);

        assertThat(allowed).isFalse();
    }

    // ── remove ──────────────────────────────────────────────────────

    @Test
    void remove_shouldSetStatusRemovedAndTimestamp() {
        given(beneficiaryRepository.findById(beneficiaryId)).willReturn(Optional.of(activeBeneficiary));

        beneficiaryService.remove(beneficiaryId, customerId);

        assertThat(activeBeneficiary.getStatus()).isEqualTo(BeneficiaryStatus.REMOVED);
        assertThat(activeBeneficiary.getRemovedAt()).isNotNull();
        verify(beneficiaryRepository).save(activeBeneficiary);
    }

    @Test
    void getById_withWrongCustomer_shouldThrowBusinessRuleException() {
        given(beneficiaryRepository.findById(beneficiaryId)).willReturn(Optional.of(activeBeneficiary));

        assertThatThrownBy(() -> beneficiaryService.getById(beneficiaryId, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("do not own");
    }

    @Test
    void getById_withUnknownId_shouldThrowBeneficiaryNotFoundException() {
        given(beneficiaryRepository.findById(beneficiaryId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> beneficiaryService.getById(beneficiaryId, customerId))
                .isInstanceOf(BeneficiaryNotFoundException.class);
    }

    @Test
    void list_shouldReturnOnlyNonRemovedBeneficiaries() {
        given(beneficiaryRepository.findByCustomerIdAndStatusNot(customerId, BeneficiaryStatus.REMOVED))
                .willReturn(List.of(activeBeneficiary));
        given(beneficiaryMapper.toResponse(any())).willReturn(BeneficiaryResponse.builder().build());

        List<BeneficiaryResponse> list = beneficiaryService.list(customerId);

        assertThat(list).hasSize(1);
    }

    private AddBeneficiaryRequest buildRequest(String accountNumber, String ifscCode) {
        AddBeneficiaryRequest request = new AddBeneficiaryRequest();
        request.setAccountNumber(accountNumber);
        request.setIfscCode(ifscCode);
        request.setBeneficiaryName("Test Beneficiary");
        return request;
    }
}
