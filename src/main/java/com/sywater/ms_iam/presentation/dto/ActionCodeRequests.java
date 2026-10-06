package com.sywater.ms_iam.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ActionCodeRequests {

    private ActionCodeRequests() {
    }

    public record RequestCode(@NotBlank @Size(max = 30) String action) {}

    public record VerifyCode(@NotBlank @Size(max = 30) String action, @NotBlank @Size(max = 10) String code) {}
}
