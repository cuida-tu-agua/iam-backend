package com.sywater.ms_iam.infrastructure.google;

import com.sywater.ms_iam.application.port.out.GoogleLoginStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** HU-004: attempts and hand-off codes live in Redis with their TTL; GETDEL makes each one single-use. */
@Component
public class RedisGoogleLoginStore implements GoogleLoginStore {

    static final String ATTEMPT_PREFIX = "iam:google:state:";
    static final String HANDOFF_PREFIX = "iam:google:handoff:";

    private final StringRedisTemplate redis;

    public RedisGoogleLoginStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void saveAttempt(String state, PendingAttempt attempt, Duration ttl) {
        // The verifier is URL-safe base64 and the redirect a validated URI: neither has a line break
        redis.opsForValue().set(ATTEMPT_PREFIX + state, attempt.codeVerifier() + "\n" + attempt.appRedirect(), ttl);
    }

    @Override
    public Optional<PendingAttempt> takeAttempt(String state) {
        String value = redis.opsForValue().getAndDelete(ATTEMPT_PREFIX + state);
        if (value == null) return Optional.empty();
        int split = value.indexOf('\n');
        if (split < 0) return Optional.empty();
        return Optional.of(new PendingAttempt(value.substring(split + 1), value.substring(0, split)));
    }

    @Override
    public void saveHandoff(String codeHash, UUID userId, Duration ttl) {
        redis.opsForValue().set(HANDOFF_PREFIX + codeHash, userId.toString(), ttl);
    }

    @Override
    public Optional<UUID> takeHandoff(String codeHash) {
        String value = redis.opsForValue().getAndDelete(HANDOFF_PREFIX + codeHash);
        return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
    }
}
