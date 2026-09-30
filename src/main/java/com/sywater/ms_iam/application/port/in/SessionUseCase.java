package com.sywater.ms_iam.application.port.in;

import com.sywater.ms_iam.application.dto.AuthResult;
import com.sywater.ms_iam.application.dto.RequestContext;

import java.time.Instant;
import java.util.UUID;

public interface SessionUseCase {

    AuthResult refresh(String refreshToken, RequestContext context);

    void logout(UUID userId, String accessTokenId, Instant accessTokenExpiresAt, String refreshToken, RequestContext context);
}
