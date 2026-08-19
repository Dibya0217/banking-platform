package com.banking.upi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upi_transactions", schema = "upi")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpiTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "upi_id", nullable = false)
    private UUID upiId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "payer_vpa", nullable = false, length = 100)
    private String payerVpa;

    @Column(name = "payee_vpa", nullable = false, length = 100)
    private String payeeVpa;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String remarks;

    @Column(nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
