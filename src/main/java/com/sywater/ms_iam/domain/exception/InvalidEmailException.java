package com.sywater.ms_iam.domain.exception;

public class InvalidEmailException extends DomainException {
    public InvalidEmailException() {
        super("user.invalid_email", "The e-mail address is not valid.");
    }
}
