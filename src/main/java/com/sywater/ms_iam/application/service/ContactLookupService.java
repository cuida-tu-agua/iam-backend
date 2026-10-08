package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.ContactView;
import com.sywater.ms_iam.application.port.in.ContactLookupUseCase;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import com.sywater.ms_iam.domain.model.User;

import java.util.UUID;

public class ContactLookupService implements ContactLookupUseCase {

    private final UserRepository users;

    public ContactLookupService(UserRepository users) {
        this.users = users;
    }

    /**
     * A deleted account has no contact (its address was erased). An unverified one is not trusted yet.
     * A BLOCKED account still has one: the water alerts of its devices keep mattering.
     */
    @Override
    public ContactView findContact(UUID userId) {
        User user = users.findById(userId)
                .filter(u -> !u.isDeleted() && u.isEmailVerified())
                .orElseThrow(UserNotFoundException::new);
        return new ContactView(user.id(), user.email().value(), user.fullName());
    }
}
