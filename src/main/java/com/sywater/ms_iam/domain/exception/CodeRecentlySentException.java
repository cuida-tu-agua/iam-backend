package com.sywater.ms_iam.domain.exception;

import java.time.Duration;

public class CodeRecentlySentException extends DomainException {

    private final Duration retryAfter;

    public CodeRecentlySentException(Duration retryAfter) {
        super("auth.code_recently_sent", "A code was just sent. Wait before asking for another.");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
