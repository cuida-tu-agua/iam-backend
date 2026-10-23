package com.sywater.ms_iam.infrastructure.persistence.adapter;

import com.sywater.ms_iam.application.port.out.PhoneVerificationRepository;
import com.sywater.ms_iam.domain.model.PhoneChannel;
import com.sywater.ms_iam.domain.model.PhoneNumber;
import com.sywater.ms_iam.infrastructure.persistence.entity.ActionCodeJpaEntity;
import com.sywater.ms_iam.infrastructure.persistence.jpa.SpringActionCodeJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** HU-028: phone codes live in security.action_codes (release v1.5 of ms-iam-db) with action PHONE_VERIFY. */
@Component
public class JpaPhoneVerificationRepositoryAdapter implements PhoneVerificationRepository {

    static final String ACTION = "PHONE_VERIFY";

    private final SpringActionCodeJpaRepository codes;

    public JpaPhoneVerificationRepositoryAdapter(SpringActionCodeJpaRepository codes) {
        this.codes = codes;
    }

    @Override
    public void issue(UUID userId, String codeHash, PhoneNumber phone, PhoneChannel channel, Instant expiresAt, Instant now) {
        codes.expireActive(userId, ACTION, now);
        codes.save(ActionCodeJpaEntity.createForPhone(userId, codeHash, phone.value(), channel.name(), expiresAt, now));
    }

    @Override
    public Optional<PendingCode> findActive(UUID userId, Instant now) {
        return codes.findFirstByUserIdAndActionAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, ACTION, now)
                .map(c -> new PendingCode(c.getId(), c.getTokenHash(), c.getExpiresAt(), c.getFailedAttempts(),
                        new PhoneNumber(c.getTargetPhone()), PhoneChannel.valueOf(c.getChannel())));
    }

    @Override
    public Optional<Instant> lastIssuedAt(UUID userId) {
        return codes.findFirstByUserIdAndActionOrderByCreatedAtDesc(userId, ACTION).map(ActionCodeJpaEntity::getCreatedAt);
    }

    @Override
    public int registerFailedAttempt(long codeId) {
        codes.incrementFailedAttempts(codeId);
        return codes.findFailedAttempts(codeId);
    }

    @Override
    public boolean consume(long codeId, Instant now) {
        return codes.markUsed(codeId, now) == 1;
    }
}
