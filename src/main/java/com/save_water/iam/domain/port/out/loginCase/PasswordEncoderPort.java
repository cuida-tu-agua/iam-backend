package com.save_water.iam.domain.port.out.loginCase;

public interface PasswordEncoderPort {
    boolean matches(String rawPassword, String encodedPassword);
}