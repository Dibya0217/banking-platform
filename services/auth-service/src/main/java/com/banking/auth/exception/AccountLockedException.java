package com.banking.auth.exception;

import com.banking.common.exception.BankingException;

import java.time.Instant;

public class AccountLockedException extends BankingException {

    public AccountLockedException(Instant lockedUntil) {
        super("ACCOUNT_LOCKED", "Account is locked until " + lockedUntil + ". Too many failed attempts.");
    }
}
