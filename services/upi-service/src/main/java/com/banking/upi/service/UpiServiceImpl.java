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
import com.banking.upi.exception.UpiNotFoundException;
import com.banking.upi.mapper.UpiMapper;
import com.banking.upi.repository.UpiIdRepository;
import com.banking.upi.repository.UpiTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpiServiceImpl implements UpiService {

    private static final String LOCK_PREFIX = "upi:lock:pin:";

    private final UpiIdRepository upiIdRepository;
    private final UpiTransactionRepository upiTransactionRepository;
    private final UpiPinEncryptor pinEncryptor;
    private final DailyLimitService dailyLimitService;
    private final TransactionServiceClient transactionServiceClient;
    private final UpiMapper upiMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public UpiIdResponse createVpa(CreateVpaRequest request, UUID customerId) {
        String vpa = request.getVpa().toLowerCase();

        if (upiIdRepository.existsByVpa(vpa)) {
            throw new BusinessRuleException("VPA_ALREADY_EXISTS", "VPA '" + vpa + "' is already taken");
        }

        UpiId upiId = UpiId.builder()
                .customerId(customerId)
                .accountId(request.getAccountId())
                .vpa(vpa)
                .pinHash(pinEncryptor.hash(request.getPin()))
                .dailyLimit(request.getDailyLimit() != null ? request.getDailyLimit() : new BigDecimal("100000.00"))
                .status(UpiStatus.ACTIVE)
                .build();

        upiId = upiIdRepository.save(upiId);
        log.info("VPA created: vpa={}, customerId={}", vpa, customerId);
        return upiMapper.toResponse(upiId);
    }

    @Override
    @Transactional
    public void changePin(UUID upiIdId, ChangePinRequest request, UUID customerId) {
        String lockKey = LOCK_PREFIX + upiIdId;

        // Distributed lock via Redis SET NX with 10s TTL
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", Duration.ofSeconds(10));
        if (Boolean.FALSE.equals(acquired)) {
            throw new BusinessRuleException("PIN_CHANGE_IN_PROGRESS", "Another PIN change is in progress. Please retry.");
        }

        try {
            UpiId upiId = findOwnedUpiId(upiIdId, customerId);

            if (!pinEncryptor.verify(request.getCurrentPin(), upiId.getPinHash())) {
                throw new BusinessRuleException("INVALID_PIN", "Current PIN is incorrect");
            }

            upiId.setPinHash(pinEncryptor.hash(request.getNewPin()));
            upiIdRepository.save(upiId);
            log.info("PIN changed for upiId={}", upiIdId);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Override
    @Transactional
    public UpiTransactionResponse transfer(UpiTransferRequest request, UUID initiatedBy) {
        if (request.getPayerVpa().equalsIgnoreCase(request.getPayeeVpa())) {
            throw new BusinessRuleException("SELF_TRANSFER", "Cannot transfer to the same VPA");
        }

        UpiId payerUpi = upiIdRepository.findByVpa(request.getPayerVpa().toLowerCase())
                .orElseThrow(() -> new UpiNotFoundException(request.getPayerVpa()));
        UpiId payeeUpi = upiIdRepository.findByVpa(request.getPayeeVpa().toLowerCase())
                .orElseThrow(() -> new UpiNotFoundException(request.getPayeeVpa()));

        if (payerUpi.getStatus() != UpiStatus.ACTIVE) {
            throw new BusinessRuleException("UPI_NOT_ACTIVE", "Payer VPA is not active");
        }
        if (!payerUpi.getCustomerId().equals(initiatedBy)) {
            throw new BusinessRuleException("ACCESS_DENIED", "You do not own payer VPA");
        }

        // PIN verification
        if (!pinEncryptor.verify(request.getPin(), payerUpi.getPinHash())) {
            throw new BusinessRuleException("INVALID_PIN", "UPI PIN is incorrect");
        }

        // Daily limit check
        BigDecimal usedToday = dailyLimitService.getUsedToday(request.getPayerVpa());
        BigDecimal projectedUsage = usedToday.add(request.getAmount());
        if (projectedUsage.compareTo(payerUpi.getDailyLimit()) > 0) {
            throw new BusinessRuleException("DAILY_LIMIT_EXCEEDED",
                    String.format("Daily limit of ₹%.2f exceeded. Used: ₹%.2f, Requested: ₹%.2f",
                            payerUpi.getDailyLimit(), usedToday, request.getAmount()));
        }

        // Delegate to transaction service
        UUID transactionId = transactionServiceClient.initiateTransfer(
                payerUpi.getAccountId(), payeeUpi.getAccountId(),
                request, initiatedBy, request.getIdempotencyKey());

        // Increment daily usage
        dailyLimitService.addUsage(request.getPayerVpa(), request.getAmount());

        // Record UPI transaction
        UpiTransaction upiTxn = UpiTransaction.builder()
                .upiId(payerUpi.getId())
                .transactionId(transactionId)
                .payerVpa(request.getPayerVpa().toLowerCase())
                .payeeVpa(request.getPayeeVpa().toLowerCase())
                .amount(request.getAmount())
                .remarks(request.getRemarks())
                .status("PENDING")
                .build();

        upiTxn = upiTransactionRepository.save(upiTxn);
        log.info("UPI transfer initiated: txnId={}, payer={}, payee={}, amount={}",
                transactionId, request.getPayerVpa(), request.getPayeeVpa(), request.getAmount());

        return upiMapper.toResponse(upiTxn);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UpiIdResponse> listVpas(UUID customerId) {
        return upiIdRepository.findByCustomerId(customerId).stream()
                .map(upiMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UpiTransactionResponse> getTransactions(UUID upiIdId, UUID customerId, Pageable pageable) {
        findOwnedUpiId(upiIdId, customerId);
        return upiTransactionRepository.findByUpiId(upiIdId, pageable)
                .map(upiMapper::toResponse);
    }

    private UpiId findOwnedUpiId(UUID upiIdId, UUID customerId) {
        UpiId upiId = upiIdRepository.findById(upiIdId)
                .orElseThrow(() -> new UpiNotFoundException(upiIdId));
        if (!upiId.getCustomerId().equals(customerId)) {
            throw new BusinessRuleException("ACCESS_DENIED", "You do not own this VPA");
        }
        return upiId;
    }
}
