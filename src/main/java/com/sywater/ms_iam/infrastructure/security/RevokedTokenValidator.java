package com.sywater.ms_iam.infrastructure.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class RevokedTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "The token was revoked.", null);
    private static final OAuth2Error UNAVAILABLE = new OAuth2Error("invalid_token", "Could not check the token.", null);

    private final RedisTokenRevocationStore store;

    public RevokedTokenValidator(RedisTokenRevocationStore store) {
        this.store = store;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            return store.isRevoked(jwt.getId(), jwt.getSubject(), jwt.getIssuedAt())
                    ? OAuth2TokenValidatorResult.failure(REVOKED)
                    : OAuth2TokenValidatorResult.success();
        } catch (RuntimeException redisDown) {
            return OAuth2TokenValidatorResult.failure(UNAVAILABLE);
        }
    }
}
