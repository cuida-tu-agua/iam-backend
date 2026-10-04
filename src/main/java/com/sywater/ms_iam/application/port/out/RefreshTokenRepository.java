package com.sywater.ms_iam.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    record StoredRefreshToken(long id, UUID userId, Instant expiresAt, Instant revokedAt) {}

    void save(UUID userId, String tokenHash, String deviceInfo, Instant expiresAt, Instant createdAt);

    Optional<StoredRefreshToken> findByHash(String tokenHash);

    boolean revokeIfActive(long id, Instant now);

    int revokeAllForUser(UUID userId, Instant now);
}
