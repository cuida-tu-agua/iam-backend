package com.sywater.ms_iam.domain.model;

import com.sywater.ms_iam.domain.exception.WeakPasswordException;

import java.util.ArrayList;
import java.util.List;


public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void validate(String raw) {
        List<String> unmet = new ArrayList<>();
        String value = raw == null ? "" : raw;

        if (value.length() < MIN_LENGTH) unmet.add("MIN_LENGTH");
        if (value.length() > MAX_LENGTH) unmet.add("MAX_LENGTH");
        if (value.chars().noneMatch(Character::isUpperCase)) unmet.add("UPPERCASE");
        if (value.chars().noneMatch(Character::isDigit)) unmet.add("DIGIT");
        if (value.chars().allMatch(c -> Character.isLetterOrDigit(c) || Character.isWhitespace(c))) unmet.add("SPECIAL");

        if (!unmet.isEmpty()) throw new WeakPasswordException(unmet);
    }
}
