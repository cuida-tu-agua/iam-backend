package com.sywater.ms_iam.domain.exception;

/**
 * HU-004: the sign-in with Google could not be completed: the attempt expired or was already used, Google rejected
 * the code, or the Google e-mail is not verified. HTTP 401.
 */
public class GoogleSignInFailedException extends DomainException {
    public GoogleSignInFailedException(String message) {
        super("auth.google_failed", message);
    }
}
