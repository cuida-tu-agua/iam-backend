package com.sywater.ms_iam.application.dto;

/** How the account can be signed in: with its password, with its Google account, or both. */
public record SignInMethods(boolean password, boolean google) {}
