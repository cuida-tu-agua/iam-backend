package com.sywater.ms_iam.domain.exception;

public class InvalidCodeException extends DomainException {

    private final int remainingAttempts;

    public InvalidCodeException(int remainingAttempts) {
        super("auth.invalid_code", "The code is not correct.");
        this.remainingAttempts = remainingAttempts;
    }

    public int remainingAttempts() {
        return remainingAttempts;
    }
}
