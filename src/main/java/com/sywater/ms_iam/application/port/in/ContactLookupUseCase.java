package com.sywater.ms_iam.application.port.in;

import com.sywater.ms_iam.application.dto.ContactView;

import java.util.UUID;

public interface ContactLookupUseCase {

    /**
     * The e-mail of a user, for services that notify them (ms-notification).
     *
     * @throws com.sywater.ms_iam.domain.exception.UserNotFoundException deleted or unverified accounts have no contact
     */
    ContactView findContact(UUID userId);

    /**
     * The account registered with that e-mail, for ms-places (collaborator invitations). Same rules as
     * {@link #findContact}: a deleted or unverified account does not exist for the other services.
     *
     * @throws com.sywater.ms_iam.domain.exception.UserNotFoundException nobody (usable) has that e-mail, or it is not an e-mail
     */
    ContactView findContactByEmail(String email);
}
