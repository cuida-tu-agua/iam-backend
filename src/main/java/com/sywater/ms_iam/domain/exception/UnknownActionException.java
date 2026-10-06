package com.sywater.ms_iam.domain.exception;

public class UnknownActionException extends DomainException {
    public UnknownActionException(String action) {
        super("action.unknown", "Unknown action: " + action);
    }
}
