package com.banking.transaction.entity;

import com.banking.common.exception.BusinessRuleException;

public enum TransactionStatus {

    PENDING {
        @Override public void validateTransition(TransactionStatus next) {
            if (next != FRAUD_CHECKING && next != DEBIT_PENDING && next != CREDIT_PENDING
                    && next != COMPLETED && next != FAILED) {
                reject(this, next);
            }
        }
    },
    FRAUD_CHECKING {
        @Override public void validateTransition(TransactionStatus next) {
            if (next != DEBIT_PENDING && next != FAILED && next != MANUAL_REVIEW) {
                reject(this, next);
            }
        }
    },
    DEBIT_PENDING {
        @Override public void validateTransition(TransactionStatus next) {
            if (next != CREDIT_PENDING && next != COMPLETED && next != FAILED) {
                reject(this, next);
            }
        }
    },
    CREDIT_PENDING {
        @Override public void validateTransition(TransactionStatus next) {
            if (next != COMPLETED && next != FAILED) {
                reject(this, next);
            }
        }
    },
    COMPLETED {
        @Override public void validateTransition(TransactionStatus next) {
            if (next != REVERSED) {
                reject(this, next);
            }
        }
    },
    FAILED {
        @Override public void validateTransition(TransactionStatus next) {
            reject(this, next);
        }
    },
    REVERSED {
        @Override public void validateTransition(TransactionStatus next) {
            reject(this, next);
        }
    },
    MANUAL_REVIEW {
        @Override public void validateTransition(TransactionStatus next) {
            if (next != DEBIT_PENDING && next != FAILED) {
                reject(this, next);
            }
        }
    };

    public abstract void validateTransition(TransactionStatus next);

    protected void reject(TransactionStatus from, TransactionStatus to) {
        throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                "Cannot transition transaction from " + from + " to " + to);
    }
}
