package com.sywater.ms_iam.domain.exception;

/** HU-004: "Continuar con Google" is not configured on this server (no client id / secret). HTTP 503. */
public class GoogleSignInDisabledException extends DomainException {
    public GoogleSignInDisabledException() {
        super("auth.google_disabled", "Sign-in with Google is not available on this server.");
    }
}
