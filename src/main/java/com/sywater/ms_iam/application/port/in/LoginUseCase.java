package com.sywater.ms_iam.application.port.in;

import com.sywater.ms_iam.application.dto.AuthResult;
import com.sywater.ms_iam.application.dto.RequestContext;

public interface LoginUseCase {

    AuthResult login(String email, String password, RequestContext context);
}
