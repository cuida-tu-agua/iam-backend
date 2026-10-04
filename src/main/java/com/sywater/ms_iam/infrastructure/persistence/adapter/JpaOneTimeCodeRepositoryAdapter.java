package com.sywater.ms_iam.infrastructure.persistence.adapter;

import com.sywater.ms_iam.application.port.out.OneTimeCodeRepository;
import com.sywater.ms_iam.infrastructure.persistence.entity.EmailVerificationJpaEntity;
import com.sywater.ms_iam.infrastructure.persistence.entity.PasswordResetJpaEntity;
import com.sywater.ms_iam.infrastructure.persistence.jpa.SpringEmailVerificationJpaRepository;
import com.sywater.ms_iam.infrastructure.persistence.jpa.SpringPasswordResetJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
@Component
public class JpaOneTimeCodeRepositoryAdapter implements OneTimeCodeRepository {

    private final SpringEmailVerificationJpaRepository verifications;
    private final SpringPasswordResetJpaRepository resets;

    public JpaOneTimeCodeRepositoryAdapter(SpringEmailVerificationJpaRepository verifications,
                                           SpringPasswordResetJpaRepository resets) {
        this.verifications = verifications;
        this.resets = resets;
    }

    @Override
    public void issue(Purpose purpose, UUID userId, String codeHash, Instant expiresAt, Instant now) {
        switch (purpose) {
            case EMAIL_VERIFICATION -> {
                verifications.expireActive(userId, now);
                verifications.save(EmailVerificationJpaEntity.create(userId, codeHash, expiresAt, now));
            }
            case PASSWORD_RESET -> {
                resets.expireActive(userId, now);
                resets.save(PasswordResetJpaEntity.create(userId, codeHash, expiresAt, now));
            }
        }
    }

    @Override
    public Optional<StoredCode> findActive(Purpose purpose, UUID userId, Instant now) {
        return switch (purpose) {
            case EMAIL_VERIFICATION -> verifications
                    .findFirstByUserIdAndVerifiedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, now)
                    .map(c -> new StoredCode(c.getId(), c.getTokenHash(), c.getExpiresAt(), c.getFailedAttempts()));
            case PASSWORD_RESET -> resets
                    .findFirstByUserIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, now)
                    .map(c -> new StoredCode(c.getId(), c.getTokenHash(), c.getExpiresAt(), c.getFailedAttempts()));
        };
    }

    @Override
    public Optional<Instant> lastIssuedAt(Purpose purpose, UUID userId) {
        return switch (purpose) {
            case EMAIL_VERIFICATION -> verifications.findFirstByUserIdOrderByCreatedAtDesc(userId)
                    .map(EmailVerificationJpaEntity::getCreatedAt);
            case PASSWORD_RESET -> resets.findFirstByUserIdOrderByCreatedAtDesc(userId)
                    .map(PasswordResetJpaEntity::getCreatedAt);
        };
    }

    @Override
    public int registerFailedAttempt(Purpose purpose, long codeId) {
        return switch (purpose) {
            case EMAIL_VERIFICATION -> {
                verifications.incrementFailedAttempts(codeId);
                yield verifications.findFailedAttempts(codeId);
            }
            case PASSWORD_RESET -> {
                resets.incrementFailedAttempts(codeId);
                yield resets.findFailedAttempts(codeId);
            }
        };
    }

    @Override
    public boolean consume(Purpose purpose, long codeId, Instant now) {
        return switch (purpose) {
            case EMAIL_VERIFICATION -> verifications.markUsed(codeId, now) == 1;
            case PASSWORD_RESET -> resets.markUsed(codeId, now) == 1;
        };
    }
}

