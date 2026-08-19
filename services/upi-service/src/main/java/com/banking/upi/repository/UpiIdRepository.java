package com.banking.upi.repository;

import com.banking.upi.entity.UpiId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UpiIdRepository extends JpaRepository<UpiId, UUID> {

    Optional<UpiId> findByVpa(String vpa);

    List<UpiId> findByCustomerId(UUID customerId);

    boolean existsByVpa(String vpa);
}
