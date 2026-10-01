package com.sywater.ms_iam.infrastructure.security;

import com.sywater.ms_iam.application.port.out.SecretGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecureRandomSecretGenerator implements SecretGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String sixDigitCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    @Override
    public String opaqueToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
