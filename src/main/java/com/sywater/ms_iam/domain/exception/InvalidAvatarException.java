package com.sywater.ms_iam.domain.exception;

public class InvalidAvatarException extends DomainException {
    public InvalidAvatarException(String message) {
        super("user.invalid_avatar", message);
    }
}
