package com.banking.customer.service;

import com.banking.customer.config.AuthServiceClient;
import com.banking.customer.dto.request.CustomerRegistrationRequest;
import com.banking.customer.dto.request.KycSubmissionRequest;
import com.banking.customer.dto.response.CustomerRegistrationResponse;
import com.banking.customer.dto.response.CustomerResponse;
import com.banking.customer.entity.Customer;
import com.banking.customer.entity.CustomerKyc;
import com.banking.customer.entity.CustomerStatus;
import com.banking.customer.entity.KycStatus;
import com.banking.customer.exception.CustomerNotFoundException;
import com.banking.customer.exception.DuplicateCustomerException;
import com.banking.customer.mapper.CustomerMapper;
import com.banking.customer.repository.CustomerKycRepository;
import com.banking.customer.repository.CustomerRepository;
import com.banking.events.customer.CustomerFrozenEvent;
import com.banking.events.customer.CustomerKycApprovedEvent;
import com.banking.events.customer.CustomerKycRejectedEvent;
import com.banking.events.customer.CustomerRegisteredEvent;
import com.banking.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerKycRepository customerKycRepository;
    private final AuthServiceClient authServiceClient;
    private final CustomerMapper customerMapper;
    private final CustomerEventPublisher eventPublisher;

    @Override
    @Transactional
    public CustomerRegistrationResponse register(CustomerRegistrationRequest request) {
        if (customerRepository.existsByEmailOrMobile(request.getEmail(), request.getMobile())) {
            throw new DuplicateCustomerException("A customer with this email or mobile already exists");
        }

        Customer customer = Customer.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .mobile(request.getMobile())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .pincode(request.getPincode())
                .status(CustomerStatus.PENDING_VERIFICATION)
                .build();

        customer = customerRepository.save(customer);
        log.info("Customer created: id={}, email={}", customer.getId(), customer.getEmail());

        // Outbox event (same transaction as customer INSERT)
        eventPublisher.publishCustomerRegistered(CustomerRegisteredEvent.of(
                customer.getId().toString(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getMobile(),
                MDC.get("correlationId")
        ));

        // Call Auth Service to create credentials (outside the Outbox — synchronous internal call)
        authServiceClient.createCredential(
                customer.getId(),
                customer.getEmail(),
                customer.getMobile(),
                request.getPassword()
        );

        return CustomerRegistrationResponse.builder()
                .customerId(customer.getId())
                .status(customer.getStatus())
                .message("Registration successful. OTP sent to mobile " + maskMobile(customer.getMobile()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getById(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> findAll(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Customer> customers;
        if (search != null && !search.isBlank()) {
            customers = customerRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                    search, search, pageable);
        } else {
            customers = customerRepository.findAll(pageable);
        }
        return customers.map(customerMapper::toResponse);
    }

    @Override
    @Transactional
    public void submitKyc(UUID customerId, KycSubmissionRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        CustomerKyc kyc = CustomerKyc.builder()
                .customerId(customer.getId())
                .documentType(request.getDocumentType())
                .documentNumber(request.getDocumentNumber())
                .documentUrl(request.getDocumentUrl())
                .build();

        customerKycRepository.save(kyc);
        log.info("KYC submitted for customerId={}, documentType={}", customerId, request.getDocumentType());
    }

    @Override
    @Transactional
    public void approveKyc(UUID customerId, UUID kycId, String adminId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        CustomerKyc kyc = customerKycRepository.findById(kycId)
                .orElseThrow(() -> new EntityNotFoundException("CustomerKyc", kycId));

        kyc.setStatus(KycStatus.APPROVED);
        kyc.setReviewedAt(Instant.now());
        kyc.setReviewedBy(UUID.fromString(adminId));
        customerKycRepository.save(kyc);

        customer.setStatus(CustomerStatus.ACTIVE);
        customerRepository.save(customer);

        eventPublisher.publishCustomerKycApproved(CustomerKycApprovedEvent.of(
                customerId.toString(),
                kycId.toString(),
                adminId,
                MDC.get("correlationId")
        ));

        log.info("KYC approved for customerId={}, kycId={}, by adminId={}", customerId, kycId, adminId);
    }

    @Override
    @Transactional
    public void rejectKyc(UUID customerId, UUID kycId, String reason, String adminId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        CustomerKyc kyc = customerKycRepository.findById(kycId)
                .orElseThrow(() -> new EntityNotFoundException("CustomerKyc", kycId));

        kyc.setStatus(KycStatus.REJECTED);
        kyc.setRejectionReason(reason);
        kyc.setReviewedAt(Instant.now());
        kyc.setReviewedBy(UUID.fromString(adminId));
        customerKycRepository.save(kyc);

        customer.setStatus(CustomerStatus.KYC_REJECTED);
        customerRepository.save(customer);

        eventPublisher.publishCustomerKycRejected(CustomerKycRejectedEvent.of(
                customerId.toString(),
                kycId.toString(),
                reason,
                adminId,
                MDC.get("correlationId")
        ));

        log.info("KYC rejected for customerId={}, kycId={}, reason={}", customerId, kycId, reason);
    }

    @Override
    @Transactional
    public void freeze(UUID customerId, String reason, String frozenBy) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        customer.setStatus(CustomerStatus.FROZEN);
        customerRepository.save(customer);

        eventPublisher.publishCustomerFrozen(CustomerFrozenEvent.of(
                customerId.toString(),
                reason,
                frozenBy,
                MDC.get("correlationId")
        ));

        log.info("Customer frozen: id={}, reason={}", customerId, reason);
    }

    @Override
    @Transactional
    public void verifyMobile(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        if (customer.getStatus() == CustomerStatus.PENDING_VERIFICATION) {
            customer.setStatus(CustomerStatus.PENDING_KYC);
            customerRepository.save(customer);
            log.info("Customer mobile verified, status updated to PENDING_KYC: id={}", customerId);
        }
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) return "****";
        return "******" + mobile.substring(mobile.length() - 4);
    }
}
