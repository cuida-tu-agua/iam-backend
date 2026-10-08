package com.sywater.ms_iam.domain.exception;

/** The caller is not another Sy Water service (missing or wrong X-Internal-Key). HTTP 403. */
public class InternalOnlyException extends DomainException {
    public InternalOnlyException() {
        super("auth.internal_only", "This endpoint can only be called by another Sy Water service.");
    }
}
