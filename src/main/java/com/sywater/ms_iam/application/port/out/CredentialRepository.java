package com.sywater.ms_iam.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository {

    Optional<String> findPasswordHash(UUID userId);

    void savePasswordHash(UUID userId, String passwordHash);
}
