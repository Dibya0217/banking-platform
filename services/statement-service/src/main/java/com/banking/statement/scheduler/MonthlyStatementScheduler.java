package com.banking.statement.scheduler;

import com.banking.statement.repository.StatementTransactionRepository;
import com.banking.statement.service.StatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class MonthlyStatementScheduler {

    private final StatementService statementService;
    private final StatementTransactionRepository statementTransactionRepository;

    @Scheduled(cron = "0 0 6 1 * *")
    public void generateMonthlyStatements() {
        // Get previous month
        LocalDate now = LocalDate.now();
        LocalDate previousMonth = now.minusMonths(1);
        int month = previousMonth.getMonthValue();
        int year = previousMonth.getYear();

        log.info("Starting monthly statement generation for {}/{}", month, year);

        List<UUID> accountIds = statementTransactionRepository.findDistinctAccountIds();
        log.info("Found {} distinct accounts to generate statements for", accountIds.size());

        int success = 0;
        int failed = 0;
        for (UUID accountId : accountIds) {
            try {
                statementService.generateStatement(accountId, month, year);
                success++;
            } catch (Exception e) {
                log.error("Failed to generate statement for account {} {}/{}: {}",
                        accountId, month, year, e.getMessage());
                failed++;
            }
        }

        log.info("Monthly statement generation completed: {} success, {} failed", success, failed);
    }
}
