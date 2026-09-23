package com.sywater.ms_iam.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "iam")
public record IamProperties(
        Jwt jwt,
        String emailVerificationTtl,
        String passwordResetTtl,
        Lockout lockout,
        Google google,
        String frontendBaseUrl
) {
    public record Jwt(
            String issuer,
            String keyId,
            String accessTtl,
            String refreshTtl,
            String publicKey,
            String privateKey
    ) {}

    public record Lockout(
            int maxAttempts,
            String duration
    ) {}

    public record Google(
            String clientIds
    ) {}
}