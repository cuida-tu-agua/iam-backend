package com.sywater.ms_iam.domain.model;

import com.sywater.ms_iam.domain.exception.InvalidUserDataException;

/**
 * HU-028: how the phone receives short messages. The user picks it when verifying the phone; the code travels by that
 * mode and the critical alerts use it afterwards, so a number that does not work for it can never be verified with it.
 */
public enum PhoneChannel {
    SMS, WHATSAPP;

    public static PhoneChannel parse(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase();
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidUserDataException("The mode must be SMS or WHATSAPP.");
        }
    }
}
