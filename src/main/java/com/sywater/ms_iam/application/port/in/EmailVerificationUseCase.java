package com.sywater.ms_iam.application.port.in;

import com.sywater.ms_iam.application.dto.CodeSent;

public interface EmailVerificationUseCase {

    void verify(String email, String code);

    CodeSent resend(String email);
}
