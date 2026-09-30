package com.sywater.ms_iam.application.port.out;

import com.sywater.ms_iam.domain.model.User;

import java.time.Instant;

public interface AccessTokenIssuer {

    record AccessToken(String value, String id, Instant expiresAt) {}

    AccessToken issue(User user, Instant now);
}
