package com.sywater.ms_iam.domain.exception;

public class EmailAlreadyRegisteredException extends DomainException {
    public EmailAlreadyRegisteredException() {
        super("auth.email_already_registered", "This e-mail is already registered.");
    }
}
