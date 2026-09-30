package com.sywater.ms_iam.application.port.out;

public interface SecretHasher {

    String hash(String secret);

    boolean matches(String secret, String expectedHash);
}
