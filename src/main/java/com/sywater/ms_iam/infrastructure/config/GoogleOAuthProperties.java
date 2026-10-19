package com.sywater.ms_iam.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * HU-004 "Continuar con Google" (iam.google.*). Without client-id and client-secret the feature is off:
 * GET /api/auth/google/status answers enabled=false and the app hides the button.
 *
 * @param clientId         OAuth client of type "Web application" in Google Cloud Console
 * @param clientSecret     its secret (only here, never in the app)
 * @param redirectUri      PUBLIC address of the callback, the same one registered in Google
 *                         (production: https://api.sy-water.tech/iam/api/auth/google/callback)
 * @param allowedRedirects app addresses that may receive the result: web origins ("https://sy-water.tech")
 *                         and mobile schemes ending in ":" ("saveyourwater:", "exp:")
 */
@ConfigurationProperties(prefix = "iam.google")
public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        List<String> allowedRedirects
) {
    public boolean configured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank()
                && redirectUri != null && !redirectUri.isBlank();
    }
}
