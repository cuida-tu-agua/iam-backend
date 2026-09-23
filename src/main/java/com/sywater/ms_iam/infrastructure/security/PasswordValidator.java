package com.sywater.ms_iam.infrastructure.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PasswordValidator {

    // Al menos 8 caracteres, 1 mayúscula, 1 minúscula, 1 dígito, 1 carácter especial
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"
    );

    public boolean isValid(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }

    public String getRequirements() {
        return "Password must contain: at least 8 characters, uppercase, lowercase, digit, and special character (@$!%*?&)";
    }
}
