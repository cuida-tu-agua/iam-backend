package com.sywater.ms_iam.domain.port.in;

import com.sywater.ms_iam.application.dto.AuthResult;

public interface LoginUseCase {

    record Command(
            String email,
            String password,
            String ip,
            String userAgent
    ) {}

    AuthResult login(Command cmd);
}