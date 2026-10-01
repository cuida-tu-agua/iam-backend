package com.sywater.ms_iam.infrastructure.security;

import com.sywater.ms_iam.application.port.out.TokenRevocationStore;
import com.sywater.ms_iam.infrastructure.config.IamProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class RedisTokenRevocationStore implements TokenRevocationStore {

    public static final String JTI_PREFIX = "iam:revoked:jti:";
    public static final String USER_PREFIX = "iam:revoked-before:";

    private final StringRedisTemplate redis;
    private final IamProperties properties;
    private final Clock clock;

    public RedisTokenRevocationStore(StringRedisTemplate redis, IamProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void revokeToken(String tokenId, Instant tokenExpiresAt) {
        Duration ttl = Duration.between(clock.instant(), tokenExpiresAt);
        if (ttl.isNegative() || ttl.isZero()) return;   // already expired: nothing to do
        redis.opsForValue().set(JTI_PREFIX + tokenId, "1", ttl.plusSeconds(60));
    }

    @Override
    public void revokeAllIssuedBefore(UUID userId, Instant moment) {
        redis.opsForValue().set(USER_PREFIX + userId, Long.toString(moment.getEpochSecond()),
                properties.jwt().accessTtl().plusSeconds(60));
    }

    /** Used by RevokedTokenValidator on every request. */
    public boolean isRevoked(String tokenId, String userId, Instant issuedAt) {
        if (tokenId != null && Boolean.TRUE.equals(redis.hasKey(JTI_PREFIX + tokenId))) return true;
        if (userId == null || issuedAt == null) return false;
        String before = redis.opsForValue().get(USER_PREFIX + userId);
        return before != null && issuedAt.getEpochSecond() <= Long.parseLong(before);
    }
}
