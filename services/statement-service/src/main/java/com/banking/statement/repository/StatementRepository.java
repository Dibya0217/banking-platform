package com.banking.statement.repository;

import com.banking.statement.entity.Statement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StatementRepository extends JpaRepository<Statement, UUID> {

    Optional<Statement> findByAccountIdAndMonthAndYear(UUID accountId, int month, int year);

    Page<Statement> findByAccountId(UUID accountId, Pageable pageable);
}
