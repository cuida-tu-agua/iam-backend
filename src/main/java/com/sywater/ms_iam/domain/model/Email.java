package com.sywater.ms_iam.domain.model;

import com.sywater.ms_iam.domain.exception.InvalidEmailException;

import java.util.Locale;
import java.util.regex.Pattern;

public record Email(String value) {

    public static final int MAX_LENGTH = 320;
    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public Email {
        if (value == null) throw new InvalidEmailException();
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new InvalidEmailException();
        }
    }

    public String masked() {
        int at = value.indexOf('@');
        String local = value.substring(0, at);
        int visible = Math.min(2, local.length());
        return local.substring(0, visible) + "*".repeat(Math.max(1, local.length() - visible)) + value.substring(at);
    }

    @Override
    public String toString() {
        return value;
    }
}
