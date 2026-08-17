package com.banking.auth.repository;

import com.banking.auth.entity.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    Optional<UserCredential> findByEmail(String email);

    Optional<UserCredential> findByMobile(String mobile);

    Optional<UserCredential> findByCustomerId(UUID customerId);

    boolean existsByEmail(String email);

    boolean existsByMobile(String mobile);
}
