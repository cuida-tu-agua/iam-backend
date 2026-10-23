package com.sywater.ms_iam.application.dto;

import java.util.UUID;

/**
 * Where to write to a user. Only for other Sy Water services (X-Internal-Key), never for the app.
 * HU-028: ms-notification sends a critical alert by SMS / WhatsApp only to a verified phone (E.164) and only by the
 * mode it was verified with ({@code phoneChannel}: SMS or WHATSAPP).
 */
public record ContactView(UUID id, String email, String fullName, String phone, boolean phoneVerified, String phoneChannel) {

    /** Without a phone (the e-mail only). */
    public ContactView(UUID id, String email, String fullName) {
        this(id, email, fullName, null, false, null);
    }
}
