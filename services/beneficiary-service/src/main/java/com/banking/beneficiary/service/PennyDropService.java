package com.banking.beneficiary.service;

import com.banking.beneficiary.entity.Beneficiary;
import com.banking.beneficiary.entity.BeneficiaryStatus;
import com.banking.beneficiary.repository.BeneficiaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PennyDropService {

    private final BeneficiaryRepository beneficiaryRepository;

    @Value("${beneficiary.cooldown-hours:24}")
    private long cooldownHours;

    /**
     * Simulates penny-drop verification asynchronously.
     * In production this would call the UPI/NEFT penny-drop API.
     * In dev: always succeeds after a short delay; sets cooldown expiry.
     */
    @Async
    @Transactional
    public void simulatePennyDrop(UUID beneficiaryId) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        beneficiaryRepository.findById(beneficiaryId).ifPresent(b -> {
            if (b.getStatus() != BeneficiaryStatus.PENDING_VERIFICATION) return;

            UUID fakeTxnId = UUID.randomUUID();
            b.setPennyDropTxnId(fakeTxnId);
            b.setStatus(BeneficiaryStatus.ACTIVE);
            b.setTransferEnabledAt(Instant.now().plus(cooldownHours, ChronoUnit.HOURS));
            beneficiaryRepository.save(b);

            log.info("Penny-drop verified beneficiary={}, transfer enabled after {} hours", beneficiaryId, cooldownHours);
        });
    }

    @Transactional
    public void completePennyDrop(UUID beneficiaryId, boolean success) {
        beneficiaryRepository.findById(beneficiaryId).ifPresent(b -> {
            if (success) {
                b.setStatus(BeneficiaryStatus.ACTIVE);
                b.setTransferEnabledAt(Instant.now().plus(cooldownHours, ChronoUnit.HOURS));
            } else {
                b.setStatus(BeneficiaryStatus.REMOVED);
                b.setRemovedAt(Instant.now());
            }
            beneficiaryRepository.save(b);
        });
    }
}
