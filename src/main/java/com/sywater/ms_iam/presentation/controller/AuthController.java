package com.sywater.ms_iam.presentation.controller;

import com.sywater.ms_iam.application.dto.AuthResult;
import com.sywater.ms_iam.application.dto.CodeSent;
import com.sywater.ms_iam.application.port.in.EmailVerificationUseCase;
import com.sywater.ms_iam.application.port.in.LoginUseCase;
import com.sywater.ms_iam.application.port.in.PasswordRecoveryUseCase;
import com.sywater.ms_iam.application.port.in.RegisterUserUseCase;
import com.sywater.ms_iam.application.port.in.SessionUseCase;
import com.sywater.ms_iam.presentation.dto.AuthRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegisterUserUseCase register;
    private final EmailVerificationUseCase verification;
    private final LoginUseCase login;
    private final SessionUseCase sessions;
    private final PasswordRecoveryUseCase recovery;

    public AuthController(RegisterUserUseCase register, EmailVerificationUseCase verification, LoginUseCase login,
                          SessionUseCase sessions, PasswordRecoveryUseCase recovery) {
        this.register = register;
        this.verification = verification;
        this.login = login;
        this.sessions = sessions;
        this.recovery = recovery;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CodeSent register(@Valid @RequestBody AuthRequests.Register body) {
        return register.register(new RegisterUserUseCase.Command(
                body.firstName(), body.lastName(), body.email(), body.phone(), body.password()));
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody AuthRequests.VerifyEmail body) {
        verification.verify(body.email(), body.code());
    }

    @PostMapping("/verify-email/resend")
    public CodeSent resendCode(@Valid @RequestBody AuthRequests.EmailOnly body) {
        return verification.resend(body.email());
    }

    @PostMapping("/login")
    public AuthResult login(@Valid @RequestBody AuthRequests.Login body, HttpServletRequest request) {
        return login.login(body.email(), body.password(), RequestContexts.from(request));
    }

    @PostMapping("/refresh")
    public AuthResult refresh(@Valid @RequestBody AuthRequests.Refresh body, HttpServletRequest request) {
        return sessions.refresh(body.refreshToken(), RequestContexts.from(request));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) AuthRequests.Logout body,
                       HttpServletRequest request) {
        sessions.logout(UUID.fromString(jwt.getSubject()), jwt.getId(), jwt.getExpiresAt(),
                body == null ? null : body.refreshToken(), RequestContexts.from(request));
    }

    @PostMapping("/password/forgot")
    public CodeSent forgotPassword(@Valid @RequestBody AuthRequests.ForgotPassword body) {
        return recovery.requestReset(body.identifier());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody AuthRequests.ResetPassword body, HttpServletRequest request) {
        recovery.resetPassword(body.identifier(), body.code(), body.newPassword(), RequestContexts.from(request));
    }
}
