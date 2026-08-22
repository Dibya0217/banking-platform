package com.banking.statement.service;

import com.banking.common.exception.EntityNotFoundException;
import com.banking.statement.dto.response.StatementResponse;
import com.banking.statement.entity.Statement;
import com.banking.statement.entity.StatementStatus;
import com.banking.statement.entity.StatementTransaction;
import com.banking.statement.repository.StatementRepository;
import com.banking.statement.repository.StatementTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class StatementServiceImplTest {

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private StatementTransactionRepository statementTransactionRepository;

    @Mock
    private PdfGenerationService pdfGenerationService;

    @Mock
    private MinioStorageService minioStorageService;

    @InjectMocks
    private StatementServiceImpl statementService;

    private UUID accountId;
    private int month;
    private int year;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        month = 8;
        year = 2026;
    }

    // ── Test 1: Happy path ────────────────────────────────────────────────────

    @Test
    void generateStatement_happyPath_createsPdfAndUploads() throws Exception {
        // No existing statement
        given(statementRepository.findByAccountIdAndMonthAndYear(accountId, month, year))
                .willReturn(Optional.empty());

        Statement pendingStatement = Statement.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .month(month)
                .year(year)
                .status(StatementStatus.PENDING)
                .totalCredits(BigDecimal.ZERO)
                .totalDebits(BigDecimal.ZERO)
                .transactionCount(0)
                .build();
        given(statementRepository.save(any(Statement.class))).willAnswer(inv -> inv.getArgument(0));

        StatementTransaction txn = StatementTransaction.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .transactionId(UUID.randomUUID())
                .amount(new BigDecimal("1000.00"))
                .direction("CREDIT")
                .transactedAt(Instant.parse("2026-08-15T10:00:00Z"))
                .build();
        given(statementTransactionRepository.findByAccountIdAndTransactedAtBetween(
                eq(accountId), any(Instant.class), any(Instant.class)))
                .willReturn(List.of(txn));

        given(statementTransactionRepository.sumBalanceBefore(eq(accountId), any(Instant.class)))
                .willReturn(BigDecimal.ZERO);

        byte[] pdfBytes = "PDF_CONTENT".getBytes();
        given(pdfGenerationService.generate(
                eq(accountId), eq(month), eq(year),
                any(), any(), any(), any(), anyList()))
                .willReturn(pdfBytes);

        // Execute
        Statement result = statementService.generateStatement(accountId, month, year);

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(StatementStatus.GENERATED);
        assertThat(result.getTotalCredits()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.getTotalDebits()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getObjectKey()).isNotNull();

        verify(minioStorageService).upload(anyString(), eq(pdfBytes), eq("application/pdf"));
        verify(statementRepository, atLeast(2)).save(any(Statement.class));
    }

    // ── Test 2: PDF generation failure sets status FAILED ─────────────────────

    @Test
    void generateStatement_failedPdfGeneration_setsStatusFailed() throws Exception {
        given(statementRepository.findByAccountIdAndMonthAndYear(accountId, month, year))
                .willReturn(Optional.empty());

        given(statementRepository.save(any(Statement.class))).willAnswer(inv -> inv.getArgument(0));

        given(statementTransactionRepository.findByAccountIdAndTransactedAtBetween(
                eq(accountId), any(Instant.class), any(Instant.class)))
                .willReturn(Collections.emptyList());

        given(statementTransactionRepository.sumBalanceBefore(eq(accountId), any(Instant.class)))
                .willReturn(BigDecimal.ZERO);

        given(pdfGenerationService.generate(
                any(), anyInt(), anyInt(),
                any(), any(), any(), any(), anyList()))
                .willThrow(new IOException("PDF generation error"));

        // Execute and verify exception is thrown
        assertThatThrownBy(() -> statementService.generateStatement(accountId, month, year))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Statement generation failed");

        // Verify save was called: once for PENDING creation, once for FAILED update
        verify(statementRepository, times(2)).save(any(Statement.class));
    }

    // ── Test 3: No transactions still generates PDF ────────────────────────────

    @Test
    void generateStatement_noTransactions_generatesEmptyStatement() throws Exception {
        given(statementRepository.findByAccountIdAndMonthAndYear(accountId, month, year))
                .willReturn(Optional.empty());

        given(statementRepository.save(any(Statement.class))).willAnswer(inv -> inv.getArgument(0));

        given(statementTransactionRepository.findByAccountIdAndTransactedAtBetween(
                eq(accountId), any(Instant.class), any(Instant.class)))
                .willReturn(Collections.emptyList());

        given(statementTransactionRepository.sumBalanceBefore(eq(accountId), any(Instant.class)))
                .willReturn(BigDecimal.ZERO);

        byte[] pdfBytes = "EMPTY_STATEMENT_PDF".getBytes();
        given(pdfGenerationService.generate(
                eq(accountId), eq(month), eq(year),
                any(), any(), any(), any(), eq(Collections.emptyList())))
                .willReturn(pdfBytes);

        Statement result = statementService.generateStatement(accountId, month, year);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(StatementStatus.GENERATED);
        assertThat(result.getTransactionCount()).isEqualTo(0);
        assertThat(result.getTotalCredits()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalDebits()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(minioStorageService).upload(anyString(), eq(pdfBytes), eq("application/pdf"));
    }

    // ── Test 4: Idempotent - already GENERATED returns existing ───────────────

    @Test
    void generateStatement_idempotent_returnsExistingGenerated() throws Exception {
        Statement existingStatement = Statement.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .month(month)
                .year(year)
                .status(StatementStatus.GENERATED)
                .objectKey("statements/" + accountId + "/" + year + "/" + month + ".pdf")
                .openingBalance(BigDecimal.ZERO)
                .closingBalance(new BigDecimal("5000.00"))
                .totalCredits(new BigDecimal("5000.00"))
                .totalDebits(BigDecimal.ZERO)
                .transactionCount(3)
                .generatedAt(Instant.now())
                .totalCredits(BigDecimal.ZERO)
                .totalDebits(BigDecimal.ZERO)
                .build();

        given(statementRepository.findByAccountIdAndMonthAndYear(accountId, month, year))
                .willReturn(Optional.of(existingStatement));

        Statement result = statementService.generateStatement(accountId, month, year);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(StatementStatus.GENERATED);

        // PDF should NOT be regenerated
        verify(pdfGenerationService, never()).generate(any(), anyInt(), anyInt(),
                any(), any(), any(), any(), anyList());
        verify(minioStorageService, never()).upload(anyString(), any(), anyString());
    }

    // ── Test 5: getStatement not found throws exception ────────────────────────

    @Test
    void getStatement_notFound_throwsException() {
        given(statementRepository.findByAccountIdAndMonthAndYear(accountId, month, year))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> statementService.getStatement(accountId, month, year))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
