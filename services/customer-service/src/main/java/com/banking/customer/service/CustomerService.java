package com.banking.customer.service;

import com.banking.customer.dto.request.CustomerRegistrationRequest;
import com.banking.customer.dto.request.KycSubmissionRequest;
import com.banking.customer.dto.response.CustomerRegistrationResponse;
import com.banking.customer.dto.response.CustomerResponse;

import java.util.UUID;

public interface CustomerService {

    CustomerRegistrationResponse register(CustomerRegistrationRequest request);

    CustomerResponse getById(UUID id);

    void submitKyc(UUID customerId, KycSubmissionRequest request);

    void approveKyc(UUID customerId, UUID kycId, String adminId);

    void rejectKyc(UUID customerId, UUID kycId, String reason, String adminId);

    void freeze(UUID customerId, String reason, String frozenBy);

    void verifyMobile(UUID customerId);
}
