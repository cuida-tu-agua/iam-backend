package com.sywater.ms_iam.domain.exception;

import java.time.Instant;

public class AccountLockedException extends DomainException {

    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super("auth.account_locked", "The account is locked until " + lockedUntil + ".");
        this.lockedUntil = lockedUntil;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }
}
