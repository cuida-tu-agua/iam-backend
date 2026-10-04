package com.sywater.ms_iam.domain.model;

import com.sywater.ms_iam.domain.exception.InvalidUserDataException;

import java.util.regex.Pattern;

public record PhoneNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("^\\+?[0-9]{7,15}$");

    public PhoneNumber {
        if (value == null) throw new InvalidUserDataException("The phone number is required.");
        value = value.replaceAll("[\\s\\-.()]", "");
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidUserDataException("The phone number must have 7 to 15 digits.");
        }
    }

    public static PhoneNumber ofNullable(String raw) {
        return raw == null || raw.isBlank() ? null : new PhoneNumber(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
