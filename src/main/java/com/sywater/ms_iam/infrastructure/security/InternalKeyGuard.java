package com.sywater.ms_iam.infrastructure.security;

import com.sywater.ms_iam.domain.exception.InternalOnlyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Service-to-service key (header X-Internal-Key), the SAME value the .NET services keep in "Internal:ApiKey".
 * Fails closed: with no key configured (or shorter than 24 characters) every call is refused.
 */
@Component
public class InternalKeyGuard {

    public static final String HEADER = "X-Internal-Key";
    private static final int MIN_LENGTH = 24;

    private final byte[] expected;

    public InternalKeyGuard(@Value("${iam.internal.api-key:}") String apiKey) {
        this.expected = apiKey == null || apiKey.length() < MIN_LENGTH ? null : apiKey.getBytes(StandardCharsets.UTF_8);
    }

    public void require(String received) {
        if (expected == null || received == null
                || !MessageDigest.isEqual(expected, received.getBytes(StandardCharsets.UTF_8))) {
            throw new InternalOnlyException();
        }
    }
}
