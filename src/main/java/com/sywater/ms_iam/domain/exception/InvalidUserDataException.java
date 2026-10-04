package com.sywater.ms_iam.domain.exception;

public class InvalidUserDataException extends DomainException {
    public InvalidUserDataException(String message) {
        super("user.invalid", message);
    }
}
