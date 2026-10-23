package com.sywater.ms_iam.application.dto;

import com.sywater.ms_iam.domain.model.Role;
import com.sywater.ms_iam.domain.model.User;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The profile the app shows. {@code phoneVerified} / {@code phoneChannel} (HU-028): whether the phone was verified and
 * by which mode (SMS / WHATSAPP) the critical alerts will reach it.
 */
public record UserView(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        boolean phoneVerified,
        String phoneChannel,
        String avatarUrl,
        boolean emailVerified,
        Set<String> roles,
        Instant createdAt
) {
    public static UserView from(User user) {
        Set<String> roles = new TreeSet<>();
        for (Role role : user.roles()) roles.add(role.name());
        return new UserView(user.id(), user.firstName(), user.lastName(), user.email().value(),
                user.phone() == null ? null : user.phone().value(),
                user.isPhoneVerified(), user.phoneChannel() == null ? null : user.phoneChannel().name(),
                user.avatarUrl(), user.isEmailVerified(), roles, user.createdAt());
    }
}
