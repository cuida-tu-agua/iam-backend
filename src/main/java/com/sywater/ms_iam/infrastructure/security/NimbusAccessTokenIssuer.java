package com.sywater.ms_iam.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sywater.ms_iam.application.port.out.AccessTokenIssuer;
import com.sywater.ms_iam.domain.model.Role;
import com.sywater.ms_iam.domain.model.User;
import com.sywater.ms_iam.infrastructure.config.IamProperties;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Component
public class NimbusAccessTokenIssuer implements AccessTokenIssuer {

    private final RSAPrivateKey privateKey;
    private final IamProperties properties;

    public NimbusAccessTokenIssuer(RSAPrivateKey privateKey, IamProperties properties) {
        this.privateKey = privateKey;
        this.properties = properties;
    }

    @Override
    public AccessToken issue(User user, Instant now) {
        Instant issuedAt = now.truncatedTo(ChronoUnit.SECONDS);   // JWT dates have second precision
        Instant expiresAt = issuedAt.plus(properties.jwt().accessTtl());
        String tokenId = UUID.randomUUID().toString();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.jwt().issuer())
                .subject(user.id().toString())
                .claim("email", user.email().value())
                .claim("name", user.fullName())
                .claim("roles", user.roles().stream().map(Role::name).sorted().toList())
                .jwtID(tokenId)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(properties.jwt().keyId()).build(), claims);
        try {
            jwt.sign(new RSASSASigner(privateKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign the access token", e);
        }
        return new AccessToken(jwt.serialize(), tokenId, expiresAt);
    }

}
