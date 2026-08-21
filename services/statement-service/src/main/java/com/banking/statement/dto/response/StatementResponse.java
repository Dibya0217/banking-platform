package com.banking.statement.dto.response;

import com.banking.statement.entity.Statement;
import com.banking.statement.entity.StatementStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class StatementResponse {

    private UUID id;
    private UUID accountId;
    private int month;
    private int year;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private int transactionCount;
    private String objectKey;
    private Instant generatedAt;
    private StatementStatus status;
    private Instant createdAt;

    public static StatementResponse from(Statement statement) {
        return StatementResponse.builder()
                .id(statement.getId())
                .accountId(statement.getAccountId())
                .month(statement.getMonth())
                .year(statement.getYear())
                .openingBalance(statement.getOpeningBalance())
                .closingBalance(statement.getClosingBalance())
                .totalCredits(statement.getTotalCredits())
                .totalDebits(statement.getTotalDebits())
                .transactionCount(statement.getTransactionCount() != null ? statement.getTransactionCount() : 0)
                .objectKey(statement.getObjectKey())
                .generatedAt(statement.getGeneratedAt())
                .status(statement.getStatus())
                .createdAt(statement.getCreatedAt())
                .build();
    }
}
