package com.sywater.ms_iam.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository {

    Optional<String> findPasswordHash(UUID userId);

    void savePasswordHash(UUID userId, String passwordHash);

    /** HU-004: the account linked to this Google account ("sub" of the ID token). */
    Optional<UUID> findUserIdByGoogleId(String googleId);

    Optional<String> findGoogleId(UUID userId);

    void saveGoogleId(UUID userId, String googleId);

    void removeGoogleId(UUID userId);
}
