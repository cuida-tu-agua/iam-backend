package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.AuthSettings;
import com.sywater.ms_iam.application.port.out.OneTimeCodeRepository;
import com.sywater.ms_iam.application.port.out.OneTimeCodeRepository.Purpose;
import com.sywater.ms_iam.application.port.out.OneTimeCodeRepository.StoredCode;
import com.sywater.ms_iam.application.port.out.SecretGenerator;
import com.sywater.ms_iam.application.port.out.SecretHasher;
import com.sywater.ms_iam.domain.exception.CodeExpiredException;
import com.sywater.ms_iam.domain.exception.CodeRecentlySentException;
import com.sywater.ms_iam.domain.exception.InvalidCodeException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public class OneTimeCodes {

    private static final Pattern SIX_DIGITS = Pattern.compile("^\\d{6}$");

    private final OneTimeCodeRepository codes;
    private final SecretGenerator generator;
    private final SecretHasher hasher;
    private final AuthSettings settings;

    public OneTimeCodes(OneTimeCodeRepository codes, SecretGenerator generator, SecretHasher hasher, AuthSettings settings) {
        this.codes = codes;
        this.generator = generator;
        this.hasher = hasher;
        this.settings = settings;
    }

    public String issue(Purpose purpose, UUID userId, Duration validFor, Instant now, boolean enforceCooldown) {
        if (enforceCooldown) {
            codes.lastIssuedAt(purpose, userId).ifPresent(last -> {
                Duration elapsed = Duration.between(last, now);
                if (elapsed.compareTo(settings.codeResendCooldown()) < 0) {
                    throw new CodeRecentlySentException(settings.codeResendCooldown().minus(elapsed));
                }
            });
        }
        String code = generator.sixDigitCode();
        codes.issue(purpose, userId, hasher.hash(userId + ":" + code), now.plus(validFor), now);
        return code;
    }

    public void verify(Purpose purpose, UUID userId, String rawCode, Instant now) {
        StoredCode stored = codes.findActive(purpose, userId, now)
                .filter(c -> c.failedAttempts() < settings.codeMaxAttempts())
                .orElseThrow(CodeExpiredException::new);

        String code = rawCode == null ? "" : rawCode.trim();
        if (!SIX_DIGITS.matcher(code).matches() || !hasher.matches(userId + ":" + code, stored.codeHash())) {
            int failures = codes.registerFailedAttempt(purpose, stored.id());
            throw new InvalidCodeException(Math.max(0, settings.codeMaxAttempts() - failures));
        }
        if (!codes.consume(purpose, stored.id(), now)) throw new CodeExpiredException(); // used by a parallel request
    }
}
