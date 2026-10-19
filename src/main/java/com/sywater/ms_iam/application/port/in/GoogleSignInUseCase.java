package com.sywater.ms_iam.application.port.in;

import com.sywater.ms_iam.application.dto.AuthResult;
import com.sywater.ms_iam.application.dto.RequestContext;

import java.net.URI;

/** HU-004: register and sign in with Google, on web and mobile. */
public interface GoogleSignInUseCase {

    /** True when the server has Google configured (the app shows the button only then). */
    boolean enabled();

    /** 1. The app asks to sign in: returns Google's consent page. appRedirect = where to come back. */
    URI start(String appRedirect);

    /**
     * 2. Google calls back with the authorization code: the account is found, linked or created, and the app address
     * is returned with "google_code" (single-use, 2 minutes) or "google_error" (Problem Details code).
     */
    URI finish(String authorizationCode, String state, String error, RequestContext context);

    /** 3. The app trades the hand-off code for its session (the same answer as POST /api/auth/login). */
    AuthResult exchange(String handoffCode, RequestContext context);
}
