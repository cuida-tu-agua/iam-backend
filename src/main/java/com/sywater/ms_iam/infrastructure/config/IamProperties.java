package com.sywater.ms_iam.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "iam")
public record IamProperties(
        Jwt jwt,
        Codes codes,
        Lockout lockout,
        Avatars avatars,
        Mail mail,
        List<String> corsAllowedOrigins
) {
    public record Jwt(String issuer, String keyId, Duration accessTtl, Duration refreshTtl,
                      String publicKey, String privateKey) {}

    public record Codes(Duration verificationTtl, Duration resetTtl, int maxAttempts, Duration resendCooldown) {}

    public record Lockout(int maxAttempts, Duration duration) {}

    public record Avatars(String storageDir, long maxBytes) {}

    public record Mail(String from) {}
}
