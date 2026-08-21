package com.banking.statement.repository;

import com.banking.statement.entity.StatementTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StatementTransactionRepository extends JpaRepository<StatementTransaction, UUID> {

    List<StatementTransaction> findByAccountIdAndTransactedAtBetween(
            UUID accountId, Instant from, Instant to);

    Optional<StatementTransaction> findTopByAccountIdOrderByTransactedAtDesc(UUID accountId);

    boolean existsByTransactionId(UUID transactionId);

    @Query("SELECT DISTINCT st.accountId FROM StatementTransaction st")
    List<UUID> findDistinctAccountIds();

    @Query("SELECT COALESCE(SUM(CASE WHEN st.direction = 'CREDIT' THEN st.amount ELSE -st.amount END), 0) " +
           "FROM StatementTransaction st WHERE st.accountId = :accountId AND st.transactedAt < :before")
    java.math.BigDecimal sumBalanceBefore(@Param("accountId") UUID accountId, @Param("before") Instant before);
}
