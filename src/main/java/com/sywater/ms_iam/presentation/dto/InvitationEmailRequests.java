package com.sywater.ms_iam.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InvitationEmailRequests {

    private InvitationEmailRequests() {
    }

    /** What ms-places sends. {@code invitationId} is only for tracing. */
    public record Send(UUID invitationId,
                       @NotBlank @Size(max = 320) String to,
                       @NotBlank @Size(max = 120) String placeName,
                       @Size(max = 200) String invitedByName,
                       @NotNull @Size(max = 10) List<@NotBlank @Size(max = 40) String> permissions,
                       @NotNull Instant expiresAt) {}
}
