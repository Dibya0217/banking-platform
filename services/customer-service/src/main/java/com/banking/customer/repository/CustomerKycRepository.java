package com.banking.customer.repository;

import com.banking.customer.entity.CustomerKyc;
import com.banking.customer.entity.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerKycRepository extends JpaRepository<CustomerKyc, UUID> {

    List<CustomerKyc> findByCustomerIdAndStatus(UUID customerId, KycStatus status);

    List<CustomerKyc> findByCustomerId(UUID customerId);
}
