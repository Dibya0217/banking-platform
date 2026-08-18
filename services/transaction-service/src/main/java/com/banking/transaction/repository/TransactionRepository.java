package com.banking.transaction.repository;

import com.banking.transaction.entity.Transaction;
import com.banking.transaction.entity.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Page<Transaction> findByFromAccountIdOrToAccountId(UUID fromAccountId, UUID toAccountId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.status = :status")
    List<Transaction> findByStatus(TransactionStatus status);
}
