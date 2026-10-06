package com.sywater.ms_iam.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OneTimeCodeRepository {

    enum Purpose { EMAIL_VERIFICATION, PASSWORD_RESET, VALVE_CLOSE }

    record StoredCode(long id, String codeHash, Instant expiresAt, int failedAttempts) {}

    void issue(Purpose purpose, UUID userId, String codeHash, Instant expiresAt, Instant now);

    Optional<StoredCode> findActive(Purpose purpose, UUID userId, Instant now);

    Optional<Instant> lastIssuedAt(Purpose purpose, UUID userId);

    int registerFailedAttempt(Purpose purpose, long codeId);

    boolean consume(Purpose purpose, long codeId, Instant now);

}
