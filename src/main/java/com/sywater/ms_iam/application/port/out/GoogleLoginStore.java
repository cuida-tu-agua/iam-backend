package com.sywater.ms_iam.application.port.out;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * HU-004: short-lived, single-use data of a sign-in with Google.
 * <ul>
 *   <li>state → where the app wants to come back and the PKCE verifier (protects against CSRF on the callback)</li>
 *   <li>hand-off code → user who already proved who they are (the app trades it for the session)</li>
 * </ul>
 * "take" reads AND deletes, so each value works once.
 */
public interface GoogleLoginStore {

    void saveAttempt(String state, PendingAttempt attempt, Duration ttl);

    Optional<PendingAttempt> takeAttempt(String state);

    void saveHandoff(String codeHash, UUID userId, Duration ttl);

    Optional<UUID> takeHandoff(String codeHash);

    /**
     * reauthUserId is set when the attempt re-confirms an account before deleting it (the account must be the one
     * linked to the Google account that comes back); null for a normal sign-in.
     */
    record PendingAttempt(String appRedirect, String codeVerifier, UUID reauthUserId) {

        public PendingAttempt(String appRedirect, String codeVerifier) {
            this(appRedirect, codeVerifier, null);
        }
    }
}
