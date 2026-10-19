package com.sywater.ms_iam.domain.exception;

/** HU-004: the address to return to after Google is not one of the allowed app addresses. HTTP 400. */
public class InvalidRedirectException extends DomainException {
    public InvalidRedirectException() {
        super("auth.invalid_redirect", "The return address is not allowed.");
    }
}
