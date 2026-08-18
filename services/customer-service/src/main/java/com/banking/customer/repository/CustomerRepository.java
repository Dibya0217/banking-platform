package com.banking.customer.repository;

import com.banking.customer.entity.Customer;
import com.banking.customer.entity.CustomerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByMobile(String mobile);

    Optional<Customer> findByPanNumber(String panNumber);

    boolean existsByEmailOrMobile(String email, String mobile);

    boolean existsByEmail(String email);

    boolean existsByMobile(String mobile);
}
