package com.sywater.ms_iam.domain.exception;

/** A service this operation depends on did not answer, so nothing was changed. HTTP 503. */
public class ExternalServiceUnavailableException extends DomainException {
    public ExternalServiceUnavailableException(String service) {
        super("service.unavailable", "The " + service + " service is not available right now. Try again later.");
    }
}
