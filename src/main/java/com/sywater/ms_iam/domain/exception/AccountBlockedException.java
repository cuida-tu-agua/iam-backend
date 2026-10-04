package com.sywater.ms_iam.domain.exception;

public class AccountBlockedException extends DomainException {
    public AccountBlockedException() {
        super("auth.account_blocked", "The account was blocked by an administrator.");
    }
}
