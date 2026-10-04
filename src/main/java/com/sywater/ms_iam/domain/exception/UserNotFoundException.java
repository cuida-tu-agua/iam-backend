package com.sywater.ms_iam.domain.exception;

public class UserNotFoundException extends DomainException {
    public UserNotFoundException() {
        super("user.not_found", "The user was not found.");
    }
}
