package com.sywater.ms_iam.application.dto;

import java.time.Instant;

/** HU-028: where the phone code went (masked number and mode) and until when it is valid. */
public record PhoneCodeSent(String maskedPhone, String channel, Instant expiresAt) {}
