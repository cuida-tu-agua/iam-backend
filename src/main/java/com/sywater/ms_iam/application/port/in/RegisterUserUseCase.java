package com.sywater.ms_iam.application.port.in;

import com.sywater.ms_iam.application.dto.CodeSent;

public interface RegisterUserUseCase {

    record Command(String firstName, String lastName, String email, String phone, String password) {}

    CodeSent register(Command command);
}
