package com.banking.account.repository;

import com.banking.account.entity.Account;
import com.banking.account.entity.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByCustomerId(UUID customerId);

    List<Account> findByCustomerIdAndStatus(UUID customerId, AccountStatus status);

    boolean existsByCustomerId(UUID customerId);

    long countByCustomerId(UUID customerId);

    boolean existsByAccountNumber(String accountNumber);
}
