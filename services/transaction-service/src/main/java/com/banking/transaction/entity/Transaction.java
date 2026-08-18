package com.banking.transaction.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions", schema = "transaction")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "from_account_id")
    private UUID fromAccountId;

    @Column(name = "to_account_id")
    private UUID toAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(length = 3, nullable = false)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(length = 255)
    private String description;

    @Column(name = "idempotency_key", length = 64, unique = true)
    private String idempotencyKey;

    @Column(name = "reference_number", length = 50, unique = true)
    private String referenceNumber;

    @Column(name = "reversal_of")
    private UUID reversalOf;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(length = 20)
    @Builder.Default
    private String channel = "API";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public void transitionTo(TransactionStatus next) {
        this.status.validateTransition(next);
        this.status = next;
        if (next == TransactionStatus.COMPLETED || next == TransactionStatus.FAILED || next == TransactionStatus.REVERSED) {
            this.completedAt = Instant.now();
        }
    }
}
