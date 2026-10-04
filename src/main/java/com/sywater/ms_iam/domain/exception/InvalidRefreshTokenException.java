package com.sywater.ms_iam.domain.exception;

public class InvalidRefreshTokenException extends DomainException {
    public InvalidRefreshTokenException() {
        super("auth.invalid_refresh_token", "The session expired. Log in again.");
    }
}
