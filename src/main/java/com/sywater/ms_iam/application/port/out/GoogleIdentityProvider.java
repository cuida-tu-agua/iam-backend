package com.sywater.ms_iam.application.port.out;

import java.net.URI;

/**
 * HU-004: Google as OAuth 2.0 / OpenID Connect provider (authorization code flow with PKCE, confidential client).
 */
public interface GoogleIdentityProvider {

    /** False when the server has no client id / secret: the button is hidden and the endpoints answer 503. */
    boolean configured();

    /** Google's consent page for this attempt. */
    URI authorizationUrl(String state, String codeVerifier);

    /**
     * Exchanges the authorization code for the tokens and returns who signed in, read from the ID token
     * (signature, issuer, audience and expiry already checked).
     */
    GoogleIdentity identify(String authorizationCode, String codeVerifier);

    record GoogleIdentity(String subject, String email, boolean emailVerified, String givenName, String familyName) {}
}
