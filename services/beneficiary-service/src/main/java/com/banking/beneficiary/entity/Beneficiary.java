package com.banking.beneficiary.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "beneficiaries", schema = "beneficiary",
        uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "account_number", "ifsc_code"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Column(name = "ifsc_code", nullable = false, length = 20)
    private String ifscCode;

    @Column(name = "beneficiary_name", nullable = false, length = 100)
    private String beneficiaryName;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "nick_name", length = 50)
    private String nickName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private BeneficiaryStatus status = BeneficiaryStatus.PENDING_VERIFICATION;

    @Column(name = "transfer_enabled_at")
    private Instant transferEnabledAt;

    @Column(name = "penny_drop_txn_id")
    private UUID pennyDropTxnId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    public boolean isTransferAllowed() {
        return status == BeneficiaryStatus.ACTIVE
                && transferEnabledAt != null
                && Instant.now().isAfter(transferEnabledAt);
    }
}
