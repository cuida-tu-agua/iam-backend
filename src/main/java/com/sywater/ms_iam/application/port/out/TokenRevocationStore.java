package com.sywater.ms_iam.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface TokenRevocationStore {

    void revokeToken(String tokenId, Instant tokenExpiresAt);

    void revokeAllIssuedBefore(UUID userId, Instant moment);
}
