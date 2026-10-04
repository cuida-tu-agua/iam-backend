package com.sywater.ms_iam.infrastructure.persistence.adapter;

import com.sywater.ms_iam.application.port.out.RefreshTokenRepository;
import com.sywater.ms_iam.infrastructure.persistence.entity.RefreshTokenJpaEntity;
import com.sywater.ms_iam.infrastructure.persistence.jpa.SpringRefreshTokenJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaRefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final SpringRefreshTokenJpaRepository tokens;

    public JpaRefreshTokenRepositoryAdapter(SpringRefreshTokenJpaRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    public void save(UUID userId, String tokenHash, String deviceInfo, Instant expiresAt, Instant createdAt) {
        tokens.save(RefreshTokenJpaEntity.create(userId, tokenHash, deviceInfo, expiresAt, createdAt));
    }

    @Override
    public Optional<StoredRefreshToken> findByHash(String tokenHash) {
        return tokens.findByTokenHash(tokenHash)
                .map(t -> new StoredRefreshToken(t.getId(), t.getUserId(), t.getExpiresAt(), t.getRevokedAt()));
    }

    @Override
    public boolean revokeIfActive(long id, Instant now) {
        return tokens.revokeIfActive(id, now) == 1;
    }

    @Override
    public int revokeAllForUser(UUID userId, Instant now) {
        return tokens.revokeAllForUser(userId, now);
    }
}
