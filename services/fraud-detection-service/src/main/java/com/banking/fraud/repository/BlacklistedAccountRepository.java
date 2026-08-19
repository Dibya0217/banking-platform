package com.banking.fraud.repository;

import com.banking.fraud.entity.BlacklistedAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlacklistedAccountRepository extends JpaRepository<BlacklistedAccount, UUID> {

    boolean existsByAccountId(UUID accountId);

    Optional<BlacklistedAccount> findByAccountId(UUID accountId);

    List<BlacklistedAccount> findAll();
}
