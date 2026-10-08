package com.sywater.ms_iam.presentation.controller;

import com.sywater.ms_iam.application.dto.ContactView;
import com.sywater.ms_iam.application.port.in.ContactLookupUseCase;
import com.sywater.ms_iam.infrastructure.security.InternalKeyGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints for OTHER Sy Water services. They carry no user token: the shared X-Internal-Key is the credential,
 * checked here (SecurityConfig lets /internal/** through so that the check is this one).
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final ContactLookupUseCase contacts;
    private final InternalKeyGuard guard;

    public InternalUserController(ContactLookupUseCase contacts, InternalKeyGuard guard) {
        this.contacts = contacts;
        this.guard = guard;
    }

    /** ms-notification asks where to write to a user. 404 only when the user has no usable contact. */
    @GetMapping("/{userId}/contact")
    public ContactView contact(@PathVariable UUID userId,
                               @RequestHeader(value = InternalKeyGuard.HEADER, required = false) String key) {
        guard.require(key);
        return contacts.findContact(userId);
    }
}
