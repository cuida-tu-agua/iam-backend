package com.sywater.ms_iam.application.dto;

import java.util.Set;
import java.util.UUID;

public record UserView(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String avatarUrl,
        Set<String> roles
) {}