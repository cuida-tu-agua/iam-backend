package com.sywater.ms_iam.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ProfileRequests {

    private ProfileRequests() {
    }

    public record UpdateProfile(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 20) String phone) {}

    public record ChangePassword(@NotBlank @Size(max = 128) String currentPassword,
                                 @NotBlank @Size(max = 128) String newPassword) {}

    /** Either the current password, or the "google_reauth" code of an account created with Google. */
    public record DeleteAccount(@Size(max = 128) String password, @Size(max = 200) String googleReauthCode) {}
}
