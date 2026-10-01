package com.sywater.ms_iam.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {

    private AuthRequests() {
    }

    public record Register(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Size(max = 320) String email,
            @Size(max = 20) String phone,
            @NotBlank @Size(max = 128) String password) {}

    public record VerifyEmail(@NotBlank @Size(max = 320) String email, @NotBlank @Size(max = 10) String code) {}

    public record EmailOnly(@NotBlank @Size(max = 320) String email) {}

    public record Login(@NotBlank @Size(max = 320) String email, @NotBlank @Size(max = 128) String password) {}

    public record Refresh(@NotBlank @Size(max = 200) String refreshToken) {}

    public record Logout(@Size(max = 200) String refreshToken) {}

    public record ForgotPassword(@NotBlank @Size(max = 320) String identifier) {}

    public record ResetPassword(
            @NotBlank @Size(max = 320) String identifier,
            @NotBlank @Size(max = 10) String code,
            @NotBlank @Size(max = 128) String newPassword) {}
}
