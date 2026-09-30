package com.sywater.ms_iam.domain.model;

import java.time.Duration;
import java.time.Instant;

public record LockoutPolicy(int maxAttempts, Duration duration) {

    public LockoutPolicy {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        if (duration == null || duration.isNegative() || duration.isZero())
            throw new IllegalArgumentException("duration must be positive");
    }

    public Instant countFailuresSince(Instant now, Instant lastSuccess, Instant previousLockEnd) {
        Instant since = now.minus(duration);
        if (lastSuccess != null && lastSuccess.isAfter(since)) since = lastSuccess;
        if (previousLockEnd != null && previousLockEnd.isAfter(since) && !previousLockEnd.isAfter(now)) since = previousLockEnd;
        return since;
    }

    /** Tries left after {@code failures} wrong passwords (never negative). */
    public int remainingAttempts(long failures) {
        return (int) Math.max(0, maxAttempts - failures);
    }

    public boolean shouldLock(long failures) {
        return failures >= maxAttempts;
    }
}
