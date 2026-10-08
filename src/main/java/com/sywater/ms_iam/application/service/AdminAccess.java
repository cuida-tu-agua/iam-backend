package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.NotAdministratorException;
import com.sywater.ms_iam.domain.model.Role;
import com.sywater.ms_iam.domain.model.User;

import java.util.UUID;

/**
 * The token says ADMIN, but the database has the last word: an administrator who was demoted, blocked or deleted
 * loses the power at once, without waiting for the token to expire. Every admin use case starts with this.
 */
public class AdminAccess {

    private final UserRepository users;

    public AdminAccess(UserRepository users) {
        this.users = users;
    }

    public void require(UUID administratorId) {
        User admin = users.findById(administratorId).filter(u -> !u.isDeleted() && !u.isBlocked())
                .orElseThrow(NotAdministratorException::new);
        if (!admin.hasRole(Role.ADMIN)) throw new NotAdministratorException();
    }
}
