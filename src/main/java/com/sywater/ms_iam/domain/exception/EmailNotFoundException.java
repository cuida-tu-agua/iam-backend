package com.sywater.ms_iam.domain.exception;

public class EmailNotFoundException extends DomainException {
    public EmailNotFoundException() {
        super("auth.email_not_found", "There is no account with this e-mail.");
    }
}
