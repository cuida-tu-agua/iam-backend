package com.sywater.ms_iam.application.port.in;

import com.sywater.ms_iam.application.dto.RequestContext;

import java.util.UUID;

public interface DeleteAccountUseCase {

    /** Accounts with a password: the current password confirms the deletion. */
    default void deleteAccount(UUID userId, String password, RequestContext context) {
        deleteAccount(userId, password, null, context);
    }

    /**
     * The password confirms the deletion, or, for accounts created with Google (no password), the code of
     * {@link com.sywater.ms_iam.application.port.in.GoogleSignInUseCase#confirmReauth} does. A code always wins.
     */
    void deleteAccount(UUID userId, String password, String googleReauthCode, RequestContext context);
}
