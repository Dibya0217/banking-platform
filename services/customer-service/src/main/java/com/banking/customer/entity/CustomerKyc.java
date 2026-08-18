package com.banking.customer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_kyc", schema = "customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerKyc {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    @Column(name = "document_number", nullable = false, length = 50)
    private String documentNumber;

    @Column(name = "document_url", nullable = false, length = 500)
    private String documentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private KycStatus status = KycStatus.PENDING;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
