package com.sywater.ms_iam.infrastructure.persistence.adapter;

import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.PhoneNumber;
import com.sywater.ms_iam.domain.model.Role;
import com.sywater.ms_iam.domain.model.User;
import com.sywater.ms_iam.infrastructure.persistence.entity.UserJpaEntity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class UserMapper {

    private UserMapper() {
    }

    static User toDomain(UserJpaEntity e, List<String> roleCodes) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String code : roleCodes) {
            try {
                roles.add(Role.valueOf(code));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return User.restore(e.getId(), e.getFirstName(), e.getLastName(), new Email(e.getEmail()),
                PhoneNumber.ofNullable(e.getPhone()), e.getAvatarUrl(), e.isEmailVerified(),
                e.getAccountLockedUntil(), e.getBlockedAt(), e.getBlockedBy(), e.getDeletedAt(), e.getCreatedAt(), e.getUpdatedAt(), roles);
    }

    static void copy(User user, UserJpaEntity e) {
        e.setFirstName(user.firstName());
        e.setLastName(user.lastName());
        e.setEmail(user.email().value());
        e.setPhone(user.phone() == null ? null : user.phone().value());
        e.setAvatarUrl(user.avatarUrl());
        e.setEmailVerified(user.isEmailVerified());
        e.setAccountLockedUntil(user.accountLockedUntil());
        e.setBlockedAt(user.blockedAt());
        e.setBlockedBy(user.blockedBy());
        e.setDeletedAt(user.deletedAt());
        e.setUpdatedAt(user.updatedAt());
        if (e.getCreatedAt() == null) e.setCreatedAt(user.createdAt());
    }
}
