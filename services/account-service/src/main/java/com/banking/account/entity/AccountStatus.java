package com.banking.account.entity;

import com.banking.common.exception.BusinessRuleException;

import java.math.BigDecimal;

public enum AccountStatus {

    ACTIVE {
        @Override
        public void validateDebit(Account account, BigDecimal amount) {
            if (amount.compareTo(account.getBalance()) > 0) {
                throw new BusinessRuleException("INSUFFICIENT_FUNDS",
                        "Insufficient funds. Available: " + account.getBalance());
            }
            if (amount.compareTo(account.getDailyDebitLimit()) > 0) {
                throw new BusinessRuleException("DAILY_LIMIT_EXCEEDED",
                        "Amount exceeds daily debit limit of " + account.getDailyDebitLimit());
            }
        }

        @Override
        public void validateCredit(Account account) {
            // ACTIVE accounts accept credits freely
        }
    },

    FROZEN {
        @Override
        public void validateDebit(Account account, BigDecimal amount) {
            throw new BusinessRuleException("ACCOUNT_FROZEN",
                    "Account " + account.getAccountNumber() + " is frozen and cannot be debited");
        }

        @Override
        public void validateCredit(Account account) {
            throw new BusinessRuleException("ACCOUNT_FROZEN",
                    "Account " + account.getAccountNumber() + " is frozen and cannot be credited");
        }
    },

    CLOSED {
        @Override
        public void validateDebit(Account account, BigDecimal amount) {
            throw new BusinessRuleException("ACCOUNT_CLOSED",
                    "Account " + account.getAccountNumber() + " is closed");
        }

        @Override
        public void validateCredit(Account account) {
            throw new BusinessRuleException("ACCOUNT_CLOSED",
                    "Account " + account.getAccountNumber() + " is closed");
        }
    };

    public abstract void validateDebit(Account account, BigDecimal amount);

    public abstract void validateCredit(Account account);
}
