package com.banking.account.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts", schema = "account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "freeze_reason", length = 255)
    private String freezeReason;

    @Column(name = "daily_debit_limit", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal dailyDebitLimit = new BigDecimal("100000.00");

    @Column(name = "minimum_balance", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal minimumBalance = new BigDecimal("1000.00");

    @Column(name = "ifsc_code", nullable = false, length = 20)
    @Builder.Default
    private String ifscCode = "BANK0000001";

    @Column(name = "branch_code", length = 10)
    private String branchCode;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public void debit(BigDecimal amount) {
        status.validateDebit(this, amount);
        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        status.validateCredit(this);
        this.balance = this.balance.add(amount);
    }
}
