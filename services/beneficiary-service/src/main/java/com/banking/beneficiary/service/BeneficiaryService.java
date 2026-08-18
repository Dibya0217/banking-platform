package com.banking.beneficiary.service;

import com.banking.beneficiary.dto.request.AddBeneficiaryRequest;
import com.banking.beneficiary.dto.response.BeneficiaryResponse;

import java.util.List;
import java.util.UUID;

public interface BeneficiaryService {

    BeneficiaryResponse add(AddBeneficiaryRequest request, UUID customerId);

    BeneficiaryResponse getById(UUID beneficiaryId, UUID customerId);

    List<BeneficiaryResponse> list(UUID customerId);

    void remove(UUID beneficiaryId, UUID customerId);

    boolean isTransferAllowed(UUID beneficiaryId, UUID customerId);
}
