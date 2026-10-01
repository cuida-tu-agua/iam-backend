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

    public record DeleteAccount(@NotBlank @Size(max = 128) String password) {}
}
