package com.sywater.ms_iam.domain.exception;

public class CurrentPasswordIncorrectException extends DomainException {
    public CurrentPasswordIncorrectException() {
        super("user.current_password_incorrect", "The current password is not correct.");
    }
}
