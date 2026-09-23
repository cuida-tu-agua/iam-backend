package com.sywater.ms_iam.domain.port.in;

import com.sywater.ms_iam.application.dto.UserView;

public interface RegisterUserUseCase {

    record Command(
            String firstName,
            String lastName,
            String email,
            String phone,
            String password
    ) {}

    UserView register(Command cmd);
}