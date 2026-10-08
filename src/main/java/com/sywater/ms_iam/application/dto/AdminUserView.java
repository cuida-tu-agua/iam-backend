package com.sywater.ms_iam.application.dto;

import com.sywater.ms_iam.domain.model.AccountStatus;
import com.sywater.ms_iam.domain.model.User;

import java.time.Instant;
import java.util.UUID;

/**
 * What an administrator sees of an account (HU-059/HU-060). No password, no tokens, no codes: only identity,
 * state and who blocked it.
 */
public record AdminUserView(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        AccountStatus status,
        Instant createdAt,
        Instant blockedAt,
        UUID blockedBy) {

    public static AdminUserView of(User user) {
        return new AdminUserView(user.id(), user.firstName(), user.lastName(), user.email().value(),
                user.phone() == null ? null : user.phone().value(), user.status(), user.createdAt(),
                user.blockedAt(), user.blockedBy());
    }
}
