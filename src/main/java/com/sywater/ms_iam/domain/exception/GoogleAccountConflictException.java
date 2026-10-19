package com.sywater.ms_iam.domain.exception;

/** HU-004: the account of this e-mail is already linked to ANOTHER Google account. HTTP 409. */
public class GoogleAccountConflictException extends DomainException {
    public GoogleAccountConflictException() {
        super("auth.google_account_conflict", "This account is already linked to a different Google account.");
    }
}
