package com.sywater.ms_iam.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sywater.ms_iam.infrastructure.config.IamProperties;
import org.springframework.stereotype.Component;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenIssuer {
    private final RSAPrivateKey privateKey;
    private final IamProperties iamProperties;

    public JwtTokenIssuer(RSAPrivateKey privateKey, IamProperties iamProperties) {
        this.privateKey = privateKey;
        this.iamProperties = iamProperties;
    }

    public String issueAccessToken(String userId, String email) throws JOSEException {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(3600); // 1 hora

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(userId)
                .claim("email", email)
                .issuer(iamProperties.jwt().issuer())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiration))
                .jwtID(UUID.randomUUID().toString())
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(iamProperties.jwt().keyId())
                        .build(),
                claimsSet
        );

        signedJWT.sign(new RSASSASigner(privateKey));
        return signedJWT.serialize();
    }
}