package com.sywater.ms_iam.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LoginAttemptRepository {

    void record(UUID userId, String email, String ip, boolean success, String failureReason, Instant at);

    long countFailuresSince(UUID userId, Instant since);

    Optional<Instant> lastSuccessAt(UUID userId);
}
