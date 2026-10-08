package com.sywater.ms_iam.application.dto;

import java.util.UUID;

/** Where to write to a user. Only for other Sy Water services (X-Internal-Key), never for the app. */
public record ContactView(UUID id, String email, String fullName) {}
