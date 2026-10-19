package com.sywater.ms_iam.infrastructure.persistence.adapter;

import com.sywater.ms_iam.application.port.out.CredentialRepository;
import com.sywater.ms_iam.infrastructure.persistence.entity.UserCredentialJpaEntity;
import com.sywater.ms_iam.infrastructure.persistence.jpa.SpringCredentialJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaCredentialRepositoryAdapter implements CredentialRepository {

    private final SpringCredentialJpaRepository credentials;
    private final Clock clock;

    public JpaCredentialRepositoryAdapter(SpringCredentialJpaRepository credentials, Clock clock) {
        this.credentials = credentials;
        this.clock = clock;
    }

    @Override
    public Optional<String> findPasswordHash(UUID userId) {
        return credentials.findByUserIdAndCredentialType(userId, UserCredentialJpaEntity.LOCAL)
                .map(UserCredentialJpaEntity::getPasswordHash);
    }

    @Override
    public void savePasswordHash(UUID userId, String passwordHash) {
        UserCredentialJpaEntity credential = credentials
                .findByUserIdAndCredentialType(userId, UserCredentialJpaEntity.LOCAL)
                .orElseGet(() -> UserCredentialJpaEntity.local(userId, passwordHash, clock.instant()));
        credential.setPasswordHash(passwordHash);
        credentials.save(credential);
    }

    @Override
    public Optional<UUID> findUserIdByGoogleId(String googleId) {
        return credentials.findByGoogleId(googleId).map(UserCredentialJpaEntity::getUserId);
    }

    @Override
    public Optional<String> findGoogleId(UUID userId) {
        return credentials.findByUserIdAndCredentialType(userId, UserCredentialJpaEntity.GOOGLE)
                .map(UserCredentialJpaEntity::getGoogleId);
    }

    @Override
    public void saveGoogleId(UUID userId, String googleId) {
        UserCredentialJpaEntity credential = credentials
                .findByUserIdAndCredentialType(userId, UserCredentialJpaEntity.GOOGLE)
                .orElseGet(() -> UserCredentialJpaEntity.google(userId, googleId, clock.instant()));
        credential.setGoogleId(googleId);
        credentials.save(credential);
    }

    @Override
    public void removeGoogleId(UUID userId) {
        credentials.findByUserIdAndCredentialType(userId, UserCredentialJpaEntity.GOOGLE).ifPresent(credentials::delete);
        credentials.flush();   // the unique index on google_id must be free before another account takes it
    }
}
