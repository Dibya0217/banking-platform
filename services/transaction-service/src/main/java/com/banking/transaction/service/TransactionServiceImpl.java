package com.banking.transaction.service;

import com.banking.common.exception.BusinessRuleException;
import com.banking.transaction.dto.request.DepositRequest;
import com.banking.transaction.dto.request.TransactionHistoryFilter;
import com.banking.transaction.dto.request.TransferRequest;
import com.banking.transaction.dto.request.WithdrawRequest;
import com.banking.transaction.dto.response.TransactionResponse;
import com.banking.transaction.entity.Transaction;
import com.banking.transaction.entity.TransactionStatus;
import com.banking.transaction.entity.TransactionType;
import com.banking.transaction.exception.TransactionNotFoundException;
import com.banking.transaction.mapper.TransactionMapper;
import com.banking.transaction.repository.TransactionRepository;
import com.banking.transaction.repository.TransactionSpecification;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final IdempotencyService idempotencyService;
    private final AccountServiceClient accountServiceClient;
    private final TransactionEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    public TransactionServiceImpl(TransactionRepository transactionRepository,
                                  TransactionMapper transactionMapper,
                                  IdempotencyService idempotencyService,
                                  AccountServiceClient accountServiceClient,
                                  TransactionEventPublisher eventPublisher,
                                  MeterRegistry meterRegistry) {
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
        this.idempotencyService = idempotencyService;
        this.accountServiceClient = accountServiceClient;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
    }

    private Counter transactionCounter(String type, String status) {
        return Counter.builder("banking_transactions_total")
                .tag("type", type)
                .tag("status", status)
                .description("Total number of banking transactions")
                .register(meterRegistry);
    }

    private DistributionSummary transactionAmountSummary(String type) {
        return DistributionSummary.builder("banking_transactions_amount")
                .tag("type", type)
                .description("Distribution of banking transaction amounts")
                .register(meterRegistry);
    }

    @Override
    @Transactional
    public TransactionResponse deposit(DepositRequest request, UUID initiatedBy) {
        Optional<TransactionResponse> cached = idempotencyService.findExisting(
                request.getIdempotencyKey(), TransactionResponse.class);
        if (cached.isPresent()) {
            log.info("Idempotent deposit: key={}", request.getIdempotencyKey());
            return cached.get();
        }

        Transaction txn = Transaction.builder()
                .toAccountId(request.getToAccountId())
                .transactionType(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .description(request.getDescription())
                .idempotencyKey(request.getIdempotencyKey())
                .referenceNumber(generateReferenceNumber())
                .initiatedBy(initiatedBy)
                .channel(request.getChannel())
                .status(TransactionStatus.PENDING)
                .build();

        txn = transactionRepository.save(txn);

        try {
            accountServiceClient.credit(request.getToAccountId(), request.getAmount(), txn.getId().toString());
            txn.transitionTo(TransactionStatus.COMPLETED);
            txn = transactionRepository.save(txn);
            transactionCounter("DEPOSIT", "COMPLETED").increment();
            transactionAmountSummary("DEPOSIT").record(request.getAmount().doubleValue());
            log.info("Deposit completed: txnId={}, account={}, amount={}", txn.getId(), request.getToAccountId(), request.getAmount());
        } catch (WebClientResponseException e) {
            txn.setFailureReason("Account service error: " + e.getStatusCode());
            txn.transitionTo(TransactionStatus.FAILED);
            txn = transactionRepository.save(txn);
            transactionCounter("DEPOSIT", "FAILED").increment();
            eventPublisher.publishFailed(txn, txn.getFailureReason());
            log.error("Deposit failed: txnId={}, reason={}", txn.getId(), txn.getFailureReason());
            throw new BusinessRuleException("DEPOSIT_FAILED", "Deposit failed: " + e.getMessage());
        }

        eventPublisher.publishCompleted(txn);
        TransactionResponse response = transactionMapper.toResponse(txn);
        idempotencyService.store(request.getIdempotencyKey(), response);
        return response;
    }

    @Override
    @Transactional
    public TransactionResponse withdraw(WithdrawRequest request, UUID initiatedBy) {
        Optional<TransactionResponse> cached = idempotencyService.findExisting(
                request.getIdempotencyKey(), TransactionResponse.class);
        if (cached.isPresent()) {
            log.info("Idempotent withdrawal: key={}", request.getIdempotencyKey());
            return cached.get();
        }

        Transaction txn = Transaction.builder()
                .fromAccountId(request.getFromAccountId())
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .description(request.getDescription())
                .idempotencyKey(request.getIdempotencyKey())
                .referenceNumber(generateReferenceNumber())
                .initiatedBy(initiatedBy)
                .channel(request.getChannel())
                .status(TransactionStatus.PENDING)
                .build();

        txn = transactionRepository.save(txn);

        try {
            accountServiceClient.debit(request.getFromAccountId(), request.getAmount(), txn.getId().toString());
            txn.transitionTo(TransactionStatus.COMPLETED);
            txn = transactionRepository.save(txn);
            transactionCounter("WITHDRAW", "COMPLETED").increment();
            transactionAmountSummary("WITHDRAW").record(request.getAmount().doubleValue());
            log.info("Withdrawal completed: txnId={}, account={}, amount={}", txn.getId(), request.getFromAccountId(), request.getAmount());
        } catch (WebClientResponseException e) {
            txn.setFailureReason("Account service error: " + e.getStatusCode());
            txn.transitionTo(TransactionStatus.FAILED);
            txn = transactionRepository.save(txn);
            transactionCounter("WITHDRAW", "FAILED").increment();
            eventPublisher.publishFailed(txn, txn.getFailureReason());
            log.error("Withdrawal failed: txnId={}, reason={}", txn.getId(), txn.getFailureReason());
            throw new BusinessRuleException("WITHDRAWAL_FAILED", "Withdrawal failed: " + e.getMessage());
        }

        eventPublisher.publishCompleted(txn);
        TransactionResponse response = transactionMapper.toResponse(txn);
        idempotencyService.store(request.getIdempotencyKey(), response);
        return response;
    }

    @Override
    @Transactional
    public TransactionResponse transfer(TransferRequest request, UUID initiatedBy) {
        Optional<TransactionResponse> cached = idempotencyService.findExisting(
                request.getIdempotencyKey(), TransactionResponse.class);
        if (cached.isPresent()) {
            log.info("Idempotent transfer: key={}", request.getIdempotencyKey());
            return cached.get();
        }

        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new BusinessRuleException("SELF_TRANSFER", "Cannot transfer to the same account");
        }

        Transaction txn = Transaction.builder()
                .fromAccountId(request.getFromAccountId())
                .toAccountId(request.getToAccountId())
                .transactionType(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .description(request.getDescription())
                .idempotencyKey(request.getIdempotencyKey())
                .referenceNumber(generateReferenceNumber())
                .initiatedBy(initiatedBy)
                .channel(request.getChannel())
                .status(TransactionStatus.PENDING)
                .build();

        txn = transactionRepository.save(txn);
        eventPublisher.publishInitiated(txn);
        transactionCounter("TRANSFER", "COMPLETED").increment();
        transactionAmountSummary("TRANSFER").record(request.getAmount().doubleValue());

        log.info("Transfer initiated (async saga): txnId={}, from={}, to={}, amount={}",
                txn.getId(), request.getFromAccountId(), request.getToAccountId(), request.getAmount());

        TransactionResponse response = transactionMapper.toResponse(txn);
        idempotencyService.store(request.getIdempotencyKey(), response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID transactionId, UUID requesterId, boolean isAdmin) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
        if (!isAdmin && !requesterId.equals(txn.getInitiatedBy())) {
            throw new BusinessRuleException("ACCESS_DENIED", "You do not have access to this transaction");
        }
        return transactionMapper.toResponse(txn);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getHistory(UUID accountId, TransactionHistoryFilter filter, Pageable pageable) {
        return transactionRepository
                .findAll(TransactionSpecification.forAccount(accountId, filter), pageable)
                .map(transactionMapper::toResponse);
    }

    @Override
    @Transactional
    public TransactionResponse reverse(UUID transactionId, UUID requesterId) {
        Transaction original = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        if (original.getStatus() != TransactionStatus.COMPLETED) {
            throw new BusinessRuleException("REVERSAL_NOT_ALLOWED",
                    "Only COMPLETED transactions can be reversed");
        }

        Transaction reversal = Transaction.builder()
                .fromAccountId(original.getToAccountId())
                .toAccountId(original.getFromAccountId())
                .transactionType(TransactionType.REVERSAL)
                .amount(original.getAmount())
                .description("Reversal of " + transactionId)
                .referenceNumber(generateReferenceNumber())
                .reversalOf(transactionId)
                .initiatedBy(requesterId)
                .channel("API")
                .status(TransactionStatus.PENDING)
                .build();

        reversal = transactionRepository.save(reversal);

        try {
            if (original.getFromAccountId() != null) {
                accountServiceClient.credit(original.getFromAccountId(), original.getAmount(), reversal.getId().toString());
            }
            if (original.getToAccountId() != null) {
                accountServiceClient.debit(original.getToAccountId(), original.getAmount(), reversal.getId().toString());
            }
        } catch (WebClientResponseException e) {
            reversal.setFailureReason("Reversal account operation failed: " + e.getStatusCode());
            reversal.transitionTo(TransactionStatus.FAILED);
            transactionRepository.save(reversal);
            throw new BusinessRuleException("REVERSAL_FAILED", "Reversal failed: " + e.getMessage());
        }

        reversal.transitionTo(TransactionStatus.COMPLETED);
        reversal = transactionRepository.save(reversal);

        original.transitionTo(TransactionStatus.REVERSED);
        original.setCompletedAt(Instant.now());
        transactionRepository.save(original);

        log.info("Transaction reversed: originalId={}, reversalId={}", transactionId, reversal.getId());
        return transactionMapper.toResponse(reversal);
    }

    private String generateReferenceNumber() {
        return "TXN" + System.currentTimeMillis() + (int) (Math.random() * 10000);
    }
}
