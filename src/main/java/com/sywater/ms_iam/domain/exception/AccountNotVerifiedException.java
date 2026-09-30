package com.sywater.ms_iam.domain.exception;

public class AccountNotVerifiedException extends DomainException {
    public AccountNotVerifiedException() {
        super("auth.account_not_verified", "The account e-mail has not been verified.");
    }
}
