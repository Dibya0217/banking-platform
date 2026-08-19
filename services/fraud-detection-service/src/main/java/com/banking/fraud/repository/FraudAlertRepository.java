package com.banking.fraud.repository;

import com.banking.fraud.entity.FraudAlert;
import com.banking.fraud.entity.FraudAlertSeverity;
import com.banking.fraud.entity.FraudAlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {

    long countByFromAccountIdAndSeverityInAndCreatedAtAfter(
            UUID fromAccountId, List<FraudAlertSeverity> severities, Instant after);

    Page<FraudAlert> findByStatus(FraudAlertStatus status, Pageable pageable);

    Page<FraudAlert> findByFromAccountId(UUID fromAccountId, Pageable pageable);
}
