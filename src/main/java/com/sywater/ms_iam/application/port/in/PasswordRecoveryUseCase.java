package com.sywater.ms_iam.application.port.in;

import com.sywater.ms_iam.application.dto.CodeSent;
import com.sywater.ms_iam.application.dto.RequestContext;

public interface PasswordRecoveryUseCase {

    CodeSent requestReset(String emailOrPhone);

    void resetPassword(String emailOrPhone, String code, String newPassword, RequestContext context);
}
