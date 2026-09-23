package com.sywater.ms_iam.application.dto;

import java.time.Instant;

public record AuthResult(
        String accessToken,
        Instant accessExpiresAt,
        String refreshToken,
        UserView user
) {}