package com.banking.beneficiary.service;

import com.banking.beneficiary.dto.request.AddBeneficiaryRequest;
import com.banking.beneficiary.dto.response.BeneficiaryResponse;
import com.banking.beneficiary.entity.Beneficiary;
import com.banking.beneficiary.entity.BeneficiaryStatus;
import com.banking.beneficiary.exception.BeneficiaryNotFoundException;
import com.banking.beneficiary.mapper.BeneficiaryMapper;
import com.banking.beneficiary.repository.BeneficiaryRepository;
import com.banking.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BeneficiaryServiceImpl implements BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final BeneficiaryMapper beneficiaryMapper;
    private final PennyDropService pennyDropService;

    @Value("${beneficiary.max-per-customer:20}")
    private int maxPerCustomer;

    @Override
    @Transactional
    public BeneficiaryResponse add(AddBeneficiaryRequest request, UUID customerId) {
        // Duplicate check
        beneficiaryRepository.findByCustomerIdAndAccountNumberAndIfscCode(
                customerId, request.getAccountNumber(), request.getIfscCode())
                .ifPresent(existing -> {
                    if (existing.getStatus() != BeneficiaryStatus.REMOVED) {
                        throw new BusinessRuleException("BENEFICIARY_ALREADY_EXISTS",
                                "Beneficiary with this account and IFSC already exists");
                    }
                });

        // Max limit
        long count = beneficiaryRepository.countByCustomerIdAndStatusNot(customerId, BeneficiaryStatus.REMOVED);
        if (count >= maxPerCustomer) {
            throw new BusinessRuleException("MAX_BENEFICIARIES_REACHED",
                    "Maximum of " + maxPerCustomer + " beneficiaries allowed per customer");
        }

        Beneficiary beneficiary = Beneficiary.builder()
                .customerId(customerId)
                .accountNumber(request.getAccountNumber())
                .ifscCode(request.getIfscCode())
                .beneficiaryName(request.getBeneficiaryName())
                .bankName(request.getBankName())
                .nickName(request.getNickName())
                .status(BeneficiaryStatus.PENDING_VERIFICATION)
                .build();

        beneficiary = beneficiaryRepository.save(beneficiary);
        log.info("Beneficiary added: id={}, customerId={}, account={}", beneficiary.getId(), customerId, request.getAccountNumber());

        // Trigger async penny-drop
        pennyDropService.simulatePennyDrop(beneficiary.getId());

        return beneficiaryMapper.toResponse(beneficiary);
    }

    @Override
    @Transactional(readOnly = true)
    public BeneficiaryResponse getById(UUID beneficiaryId, UUID customerId) {
        Beneficiary beneficiary = findOwnedBeneficiary(beneficiaryId, customerId);
        return beneficiaryMapper.toResponse(beneficiary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> list(UUID customerId) {
        return beneficiaryRepository.findByCustomerIdAndStatusNot(customerId, BeneficiaryStatus.REMOVED)
                .stream()
                .map(beneficiaryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void remove(UUID beneficiaryId, UUID customerId) {
        Beneficiary beneficiary = findOwnedBeneficiary(beneficiaryId, customerId);
        beneficiary.setStatus(BeneficiaryStatus.REMOVED);
        beneficiary.setRemovedAt(Instant.now());
        beneficiaryRepository.save(beneficiary);
        log.info("Beneficiary removed: id={}, customerId={}", beneficiaryId, customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTransferAllowed(UUID beneficiaryId, UUID customerId) {
        Beneficiary beneficiary = findOwnedBeneficiary(beneficiaryId, customerId);
        return beneficiary.isTransferAllowed();
    }

    private Beneficiary findOwnedBeneficiary(UUID beneficiaryId, UUID customerId) {
        Beneficiary beneficiary = beneficiaryRepository.findById(beneficiaryId)
                .orElseThrow(() -> new BeneficiaryNotFoundException(beneficiaryId));
        if (!beneficiary.getCustomerId().equals(customerId)) {
            throw new BusinessRuleException("ACCESS_DENIED", "You do not own this beneficiary");
        }
        return beneficiary;
    }
}
