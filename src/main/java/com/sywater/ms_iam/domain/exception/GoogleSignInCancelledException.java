package com.sywater.ms_iam.domain.exception;

/** HU-004: the person closed the Google window or did not give permission. */
public class GoogleSignInCancelledException extends DomainException {
    public GoogleSignInCancelledException() {
        super("auth.google_cancelled", "The sign-in with Google was cancelled.");
    }
}
