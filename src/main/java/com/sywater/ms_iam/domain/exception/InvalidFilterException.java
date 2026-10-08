package com.sywater.ms_iam.domain.exception;

/** A filter of a list cannot be used (e.g. asking for DELETED accounts). HTTP 400. */
public class InvalidFilterException extends DomainException {
    public InvalidFilterException(String message) {
        super("validation.invalid_filter", message);
    }
}
