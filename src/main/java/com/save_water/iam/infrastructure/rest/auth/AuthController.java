package com.save_water.iam.infrastructure.rest.auth;

// infrastructure/rest/AuthController.java

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.save_water.iam.domain.port.in.loginCase.LoginCommand;
import com.save_water.iam.domain.port.in.loginCase.LoginResult;
import com.save_water.iam.domain.port.in.loginCase.LoginUseCase;
import com.save_water.iam.infrastructure.rest.dto.LoginRequest;
import com.save_water.iam.infrastructure.rest.dto.LoginResponse;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;

    public AuthController(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResult result = loginUseCase.login(
                new LoginCommand(request.email(), request.password())
        );
        return ResponseEntity.ok(new LoginResponse(result.token(), result.email()));
    }
}