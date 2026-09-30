package com.sywater.ms_iam.domain.exception;

public class AccountNotFoundException extends DomainException {
    public AccountNotFoundException() {
        super("auth.account_not_found", "There is no account with that e-mail or phone.");
    }
}
