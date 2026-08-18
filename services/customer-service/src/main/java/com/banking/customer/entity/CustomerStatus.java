package com.banking.customer.entity;

public enum CustomerStatus {
    PENDING_VERIFICATION,
    PENDING_KYC,
    ACTIVE,
    FROZEN,
    CLOSED,
    KYC_REJECTED
}
