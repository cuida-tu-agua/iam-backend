package com.sywater.ms_iam.application.dto;

import com.sywater.ms_iam.domain.model.LockoutPolicy;

import java.time.Duration;

public record AuthSettings(
        Duration accessTokenTtl,          // HU-003: 1 h
        Duration refreshTokenTtl,         // HU-003: 7 days
        Duration verificationCodeTtl,     // HU-002: 24 h
        Duration resetCodeTtl,            // HU-005: 15 min
        int codeMaxAttempts,              // wrong tries before a code dies
        Duration codeResendCooldown,      // minimum time between two e-mails
        LockoutPolicy lockout,            // HU-003: 5 tries → 15 min
        long avatarMaxBytes               // HU-007
) {}
