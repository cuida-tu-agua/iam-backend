package com.sywater.ms_iam.domain.model;

import com.sywater.ms_iam.domain.exception.InvalidUserDataException;

import java.util.regex.Pattern;

/**
 * HU-028: the countries where Cuida Tu Agua verifies mobile phones today. Each one turns the national number the user
 * types into E.164, the format SMS / WhatsApp providers need:
 * <ul>
 *   <li>Colombia (+57): 10 digits starting with 3 ("300 111 2233" → "+573001112233").</li>
 *   <li>Ecuador (+593): 9 digits starting with 9; the leading 0 used inside the country is accepted
 *       ("099 111 2233" → "+593991112233").</li>
 * </ul>
 */
public enum PhoneCountry {
    CO("+57", Pattern.compile("^3[0-9]{9}$")),
    EC("+593", Pattern.compile("^9[0-9]{8}$"));

    private final String dialCode;
    private final Pattern mobile;

    PhoneCountry(String dialCode, Pattern mobile) {
        this.dialCode = dialCode;
        this.mobile = mobile;
    }

    public String dialCode() {
        return dialCode;
    }

    public static PhoneCountry parse(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase();
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidUserDataException("The country must be CO (Colombia) or EC (Ecuador).");
        }
    }

    /** The national number (spaces, dashes, dots or the country code are tolerated) → its E.164 phone. */
    public PhoneNumber mobile(String national) {
        String digits = national == null ? "" : national.replaceAll("[\\s\\-.()]", "");
        String code = dialCode.substring(1);
        if (digits.startsWith("+" + code)) digits = digits.substring(code.length() + 1);
        else if (digits.startsWith(code) && digits.length() > code.length() + 8) digits = digits.substring(code.length());
        if (this == EC && digits.startsWith("0")) digits = digits.substring(1);
        if (!mobile.matcher(digits).matches()) {
            throw new InvalidUserDataException(this == CO
                    ? "A Colombian mobile has 10 digits and starts with 3."
                    : "An Ecuadorian mobile has 9 digits and starts with 9 (or 10 starting with 09).");
        }
        return new PhoneNumber(dialCode + digits);
    }
}
