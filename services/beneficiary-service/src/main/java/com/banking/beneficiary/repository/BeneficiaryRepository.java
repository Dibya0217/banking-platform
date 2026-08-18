package com.banking.beneficiary.repository;

import com.banking.beneficiary.entity.Beneficiary;
import com.banking.beneficiary.entity.BeneficiaryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    List<Beneficiary> findByCustomerIdAndStatusNot(UUID customerId, BeneficiaryStatus status);

    long countByCustomerIdAndStatusNot(UUID customerId, BeneficiaryStatus status);

    Optional<Beneficiary> findByCustomerIdAndAccountNumberAndIfscCode(
            UUID customerId, String accountNumber, String ifscCode);
}
