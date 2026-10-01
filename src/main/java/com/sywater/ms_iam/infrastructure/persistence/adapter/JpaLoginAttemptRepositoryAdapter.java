package com.sywater.ms_iam.infrastructure.persistence.adapter;

import com.sywater.ms_iam.application.port.out.LoginAttemptRepository;
import com.sywater.ms_iam.infrastructure.persistence.entity.LoginAttemptJpaEntity;
import com.sywater.ms_iam.infrastructure.persistence.jpa.SpringLoginAttemptJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaLoginAttemptRepositoryAdapter implements LoginAttemptRepository {

    private static final int EMAIL_MAX = 320;
    private final SpringLoginAttemptJpaRepository attempts;

    public JpaLoginAttemptRepositoryAdapter(SpringLoginAttemptJpaRepository attempts) {
        this.attempts = attempts;
    }

    @Override
    public void record(UUID userId, String email, String ip, boolean success, String failureReason, Instant at) {
        String safeEmail = email.length() > EMAIL_MAX ? email.substring(0, EMAIL_MAX) : email;
        attempts.save(new LoginAttemptJpaEntity(userId, safeEmail, ip, success, failureReason, at));
    }

    @Override
    public long countFailuresSince(UUID userId, Instant since) {
        return attempts.countWrongPasswordsSince(userId, since);
    }

    @Override
    public Optional<Instant> lastSuccessAt(UUID userId) {
        return Optional.ofNullable(attempts.findLastSuccessAt(userId));
    }
}
