package com.sywater.ms_iam.domain.exception;

public class CodeExpiredException extends DomainException {
    public CodeExpiredException() {
        super("auth.code_expired", "The code expired or was already used. Ask for a new one.");
    }
}
