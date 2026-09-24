package com.save_water.iam.domain.port.in.loginCase;

public interface LoginUseCase {
    LoginResult login(LoginCommand command);
}