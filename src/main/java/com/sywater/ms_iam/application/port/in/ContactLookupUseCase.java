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
}
