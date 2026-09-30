package com.sywater.ms_iam.application.port.out;

public interface SecretGenerator {

    String sixDigitCode();

    String opaqueToken();
}
