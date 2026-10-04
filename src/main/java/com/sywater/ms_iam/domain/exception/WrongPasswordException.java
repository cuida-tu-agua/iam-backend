package com.sywater.ms_iam.domain.exception;

public class WrongPasswordException extends DomainException {

    private final int remainingAttempts;

    public WrongPasswordException(int remainingAttempts) {
        super("auth.wrong_password", "The password is not correct.");
        this.remainingAttempts = remainingAttempts;
    }

    public int remainingAttempts() {
        return remainingAttempts;
    }
}
