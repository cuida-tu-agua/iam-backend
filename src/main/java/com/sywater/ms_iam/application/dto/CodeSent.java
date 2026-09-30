package com.sywater.ms_iam.application.dto;

import java.time.Instant;

public record CodeSent(String maskedEmail, Instant expiresAt) {}
