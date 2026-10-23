package com.sywater.ms_iam.application.port.out;

import com.sywater.ms_iam.domain.model.PhoneChannel;
import com.sywater.ms_iam.domain.model.PhoneNumber;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * HU-028: the 6-digit codes that verify a phone (security.action_codes, action PHONE_VERIFY). Each code remembers the
 * number and the mode it was sent to, so the user's verified phone only changes once the code is confirmed.
 */
public interface PhoneVerificationRepository {

    record PendingCode(long id, String codeHash, Instant expiresAt, int failedAttempts, PhoneNumber phone, PhoneChannel channel) {}

    /** Stores a new code and expires the previous one that was still active. */
    void issue(UUID userId, String codeHash, PhoneNumber phone, PhoneChannel channel, Instant expiresAt, Instant now);

    Optional<PendingCode> findActive(UUID userId, Instant now);

    Optional<Instant> lastIssuedAt(UUID userId);

    /** @return the failed attempts after this one */
    int registerFailedAttempt(long codeId);

    /** @return false if another request used it first */
    boolean consume(long codeId, Instant now);
}
