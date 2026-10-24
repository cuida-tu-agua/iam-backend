package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.ContactView;
import com.sywater.ms_iam.application.port.in.ContactLookupUseCase;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.InvalidEmailException;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import com.sywater.ms_iam.domain.model.Email;
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
        return toContact(users.findById(userId)
                .filter(u -> !u.isDeleted() && u.isEmailVerified())
                .orElseThrow(UserNotFoundException::new));
    }

    @Override
    public ContactView findContactByEmail(String rawEmail) {
        Email email;
        try {
            email = new Email(rawEmail);
        } catch (InvalidEmailException e) {
            throw new UserNotFoundException();   // nobody has an address that is not one
        }
        return toContact(users.findByEmail(email)
                .filter(u -> !u.isDeleted() && u.isEmailVerified())
                .orElseThrow(UserNotFoundException::new));
    }

    private static ContactView toContact(User user) {
        boolean verified = user.isPhoneVerified();
        return new ContactView(user.id(), user.email().value(), user.fullName(),
                user.phone() == null ? null : user.phone().value(), verified,
                verified ? user.phoneChannel().name() : null);
    }
}
