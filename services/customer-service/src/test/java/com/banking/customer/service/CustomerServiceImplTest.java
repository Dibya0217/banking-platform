package com.banking.customer.service;

import com.banking.customer.config.AuthServiceClient;
import com.banking.customer.dto.request.CustomerRegistrationRequest;
import com.banking.customer.dto.request.KycSubmissionRequest;
import com.banking.customer.dto.response.CustomerRegistrationResponse;
import com.banking.customer.entity.*;
import com.banking.customer.exception.CustomerNotFoundException;
import com.banking.customer.exception.DuplicateCustomerException;
import com.banking.customer.mapper.CustomerMapper;
import com.banking.customer.repository.CustomerKycRepository;
import com.banking.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerKycRepository customerKycRepository;
    @Mock private AuthServiceClient authServiceClient;
    @Mock private CustomerMapper customerMapper;
    @Mock private CustomerEventPublisher eventPublisher;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private CustomerRegistrationRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = CustomerRegistrationRequest.builder()
                .fullName("Priya Sharma")
                .email("priya@example.com")
                .mobile("9876543210")
                .password("Test@1234")
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .build();
    }

    @Test
    void register_withValidRequest_shouldCreateCustomer() {
        given(customerRepository.existsByEmailOrMobile(any(), any())).willReturn(false);

        Customer saved = Customer.builder()
                .id(UUID.randomUUID())
                .fullName("Priya Sharma")
                .email("priya@example.com")
                .mobile("9876543210")
                .status(CustomerStatus.PENDING_VERIFICATION)
                .build();
        given(customerRepository.save(any())).willReturn(saved);
        doNothing().when(authServiceClient).createCredential(any(), any(), any(), any());

        CustomerRegistrationResponse response = customerService.register(validRequest);

        assertThat(response.getCustomerId()).isEqualTo(saved.getId());
        assertThat(response.getStatus()).isEqualTo(CustomerStatus.PENDING_VERIFICATION);
        verify(customerRepository).save(any(Customer.class));
        verify(eventPublisher).publishCustomerRegistered(any());
        verify(authServiceClient).createCredential(any(), any(), any(), any());
    }

    @Test
    void register_withDuplicateEmail_shouldThrowDuplicateCustomerException() {
        given(customerRepository.existsByEmailOrMobile(any(), any())).willReturn(true);

        assertThatThrownBy(() -> customerService.register(validRequest))
                .isInstanceOf(DuplicateCustomerException.class)
                .hasMessageContaining("email or mobile");

        verify(customerRepository, never()).save(any());
    }

    @Test
    void getById_withExistingId_shouldReturnCustomerResponse() {
        UUID id = UUID.randomUUID();
        Customer customer = Customer.builder().id(id).email("priya@example.com").build();
        given(customerRepository.findById(id)).willReturn(Optional.of(customer));
        given(customerMapper.toResponse(customer)).willReturn(any());

        customerService.getById(id);

        verify(customerMapper).toResponse(customer);
    }

    @Test
    void getById_withUnknownId_shouldThrowCustomerNotFoundException() {
        UUID id = UUID.randomUUID();
        given(customerRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getById(id))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void submitKyc_shouldCreateKycRecord() {
        UUID customerId = UUID.randomUUID();
        Customer customer = Customer.builder().id(customerId).build();
        given(customerRepository.findById(customerId)).willReturn(Optional.of(customer));

        KycSubmissionRequest request = KycSubmissionRequest.builder()
                .documentType(DocumentType.AADHAAR)
                .documentNumber("123456789012")
                .documentUrl("https://storage/docs/aadhaar.pdf")
                .build();

        customerService.submitKyc(customerId, request);

        verify(customerKycRepository).save(any(CustomerKyc.class));
    }

    @Test
    void verifyMobile_whenPendingVerification_shouldTransitionToPendingKyc() {
        UUID customerId = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(customerId)
                .status(CustomerStatus.PENDING_VERIFICATION)
                .build();
        given(customerRepository.findById(customerId)).willReturn(Optional.of(customer));

        customerService.verifyMobile(customerId);

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.PENDING_KYC);
        verify(customerRepository).save(customer);
    }

    @Test
    void freeze_shouldUpdateStatusAndPublishEvent() {
        UUID customerId = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(customerId)
                .status(CustomerStatus.ACTIVE)
                .build();
        given(customerRepository.findById(customerId)).willReturn(Optional.of(customer));

        customerService.freeze(customerId, "Suspicious activity", "admin-uuid");

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.FROZEN);
        verify(eventPublisher).publishCustomerFrozen(any());
    }

    @Test
    void approveKyc_shouldSetCustomerActiveAndPublishEvent() {
        UUID customerId = UUID.randomUUID();
        UUID kycId = UUID.randomUUID();
        String adminId = UUID.randomUUID().toString();

        Customer customer = Customer.builder().id(customerId).status(CustomerStatus.PENDING_KYC).build();
        CustomerKyc kyc = CustomerKyc.builder().id(kycId).customerId(customerId).status(KycStatus.PENDING).build();

        given(customerRepository.findById(customerId)).willReturn(Optional.of(customer));
        given(customerKycRepository.findById(kycId)).willReturn(Optional.of(kyc));

        customerService.approveKyc(customerId, kycId, adminId);

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(kyc.getStatus()).isEqualTo(KycStatus.APPROVED);
        verify(eventPublisher).publishCustomerKycApproved(any());
    }

    @Test
    void rejectKyc_shouldSetKycRejectedAndUpdateCustomerStatus() {
        UUID customerId = UUID.randomUUID();
        UUID kycId = UUID.randomUUID();
        String adminId = UUID.randomUUID().toString();

        Customer customer = Customer.builder().id(customerId).status(CustomerStatus.PENDING_KYC).build();
        CustomerKyc kyc = CustomerKyc.builder().id(kycId).customerId(customerId).status(KycStatus.PENDING).build();

        given(customerRepository.findById(customerId)).willReturn(Optional.of(customer));
        given(customerKycRepository.findById(kycId)).willReturn(Optional.of(kyc));

        customerService.rejectKyc(customerId, kycId, "Document unclear", adminId);

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.KYC_REJECTED);
        assertThat(kyc.getStatus()).isEqualTo(KycStatus.REJECTED);
        assertThat(kyc.getRejectionReason()).isEqualTo("Document unclear");
    }
}
