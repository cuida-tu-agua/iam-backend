package com.sywater.ms_iam.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU-028 bodies. The domain checks the country, the number and the mode (400 user.invalid with the reason). */
public final class PhoneRequests {

    private PhoneRequests() {
    }

    /**
     * @param country CO (Colombia, +57) or EC (Ecuador, +593)
     * @param phone   national number, e.g. 3001112233 or 0991112233
     * @param channel SMS or WHATSAPP: the code goes by it, and so will the critical alerts
     */
    public record RequestCode(@NotBlank @Size(max = 2) String country,
                              @NotBlank @Size(max = 20) String phone,
                              @NotBlank @Size(max = 10) String channel) {}

    public record Confirm(@NotBlank @Size(max = 10) String code) {}
}
