package com.banking.statement.service;

import com.banking.common.exception.EntityNotFoundException;
import com.banking.statement.dto.response.StatementResponse;
import com.banking.statement.entity.Statement;
import com.banking.statement.entity.StatementStatus;
import com.banking.statement.entity.StatementTransaction;
import com.banking.statement.repository.StatementRepository;
import com.banking.statement.repository.StatementTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class StatementServiceImpl implements StatementService {

    private final StatementRepository statementRepository;
    private final StatementTransactionRepository statementTransactionRepository;
    private final PdfGenerationService pdfGenerationService;
    private final MinioStorageService minioStorageService;

    @Override
    @Transactional
    public Statement generateStatement(UUID accountId, int month, int year) {
        // 1. Find or create Statement record
        Statement statement = statementRepository.findByAccountIdAndMonthAndYear(accountId, month, year)
                .orElseGet(() -> {
                    Statement s = Statement.builder()
                            .accountId(accountId)
                            .month(month)
                            .year(year)
                            .status(StatementStatus.PENDING)
                            .build();
                    return statementRepository.save(s);
                });

        // If already generated, return as-is (idempotent)
        if (StatementStatus.GENERATED.equals(statement.getStatus())) {
            log.info("Statement already generated for account {} {}/{}", accountId, month, year);
            return statement;
        }

        try {
            // 2. Compute date range for the month
            LocalDate startDate = LocalDate.of(year, month, 1);
            LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
            Instant periodStart = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant periodEnd = endDate.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();

            // 3. Query transactions in period
            List<StatementTransaction> transactions = statementTransactionRepository
                    .findByAccountIdAndTransactedAtBetween(accountId, periodStart, periodEnd);

            // 4. Compute balances
            BigDecimal openingBalance = statementTransactionRepository
                    .sumBalanceBefore(accountId, periodStart);
            if (openingBalance == null) openingBalance = BigDecimal.ZERO;

            BigDecimal totalCredits = BigDecimal.ZERO;
            BigDecimal totalDebits = BigDecimal.ZERO;
            for (StatementTransaction txn : transactions) {
                if ("CREDIT".equals(txn.getDirection())) {
                    totalCredits = totalCredits.add(txn.getAmount());
                } else {
                    totalDebits = totalDebits.add(txn.getAmount());
                }
            }
            BigDecimal closingBalance = openingBalance.add(totalCredits).subtract(totalDebits);

            // 5. Generate PDF
            byte[] pdfBytes = pdfGenerationService.generate(
                    accountId, month, year,
                    openingBalance, closingBalance,
                    totalCredits, totalDebits,
                    transactions);

            // 6. Upload to MinIO
            String objectKey = String.format("statements/%s/%d/%d.pdf", accountId, year, month);
            minioStorageService.upload(objectKey, pdfBytes, "application/pdf");

            // 7. Update statement
            statement.setOpeningBalance(openingBalance);
            statement.setClosingBalance(closingBalance);
            statement.setTotalCredits(totalCredits);
            statement.setTotalDebits(totalDebits);
            statement.setTransactionCount(transactions.size());
            statement.setObjectKey(objectKey);
            statement.setGeneratedAt(Instant.now());
            statement.setStatus(StatementStatus.GENERATED);
            return statementRepository.save(statement);

        } catch (Exception e) {
            log.error("Failed to generate statement for account {} {}/{}: {}", accountId, month, year, e.getMessage(), e);
            statement.setStatus(StatementStatus.FAILED);
            statementRepository.save(statement);
            throw new RuntimeException("Statement generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StatementResponse getStatement(UUID accountId, int month, int year) {
        Statement statement = statementRepository.findByAccountIdAndMonthAndYear(accountId, month, year)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Statement not found for account " + accountId + " period " + month + "/" + year));
        return StatementResponse.from(statement);
    }

    @Override
    @Transactional
    public StatementResponse getOrGenerate(UUID accountId, int month, int year) {
        return statementRepository.findByAccountIdAndMonthAndYear(accountId, month, year)
                .map(StatementResponse::from)
                .orElseGet(() -> StatementResponse.from(generateStatement(accountId, month, year)));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadStatement(UUID accountId, int month, int year) {
        Statement statement = statementRepository.findByAccountIdAndMonthAndYear(accountId, month, year)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Statement not found for account " + accountId + " period " + month + "/" + year));

        if (!StatementStatus.GENERATED.equals(statement.getStatus()) || statement.getObjectKey() == null) {
            throw new IllegalStateException("Statement PDF is not available yet for " + month + "/" + year);
        }

        return minioStorageService.download(statement.getObjectKey());
    }
}
