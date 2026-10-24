package com.sywater.ms_iam.presentation.controller;

import com.sywater.ms_iam.application.port.in.InvitationEmailUseCase;
import com.sywater.ms_iam.infrastructure.security.InternalKeyGuard;
import com.sywater.ms_iam.presentation.dto.InvitationEmailRequests;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** For ms-places only (X-Internal-Key): the e-mail that invites a person to collaborate in a place (HU-042). */
@RestController
@RequestMapping("/internal/invitation-emails")
public class InternalInvitationController {

    private final InvitationEmailUseCase invitations;
    private final InternalKeyGuard guard;

    public InternalInvitationController(InvitationEmailUseCase invitations, InternalKeyGuard guard) {
        this.invitations = invitations;
        this.guard = guard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void send(@RequestBody @Valid InvitationEmailRequests.Send request,
                     @RequestHeader(value = InternalKeyGuard.HEADER, required = false) String key) {
        guard.require(key);
        invitations.send(new InvitationEmailUseCase.Command(
                request.to(), request.placeName(), request.invitedByName(), request.permissions(), request.expiresAt()));
    }
}
