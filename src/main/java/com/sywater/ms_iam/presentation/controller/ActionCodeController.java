package com.sywater.ms_iam.presentation.controller;

import com.sywater.ms_iam.application.dto.CodeSent;
import com.sywater.ms_iam.application.port.in.ActionCodeUseCase;
import com.sywater.ms_iam.presentation.dto.ActionCodeRequests;
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
@RequestMapping("/api/users/me/action-codes")
public class ActionCodeController {

    private final ActionCodeUseCase actionCodes;

    public ActionCodeController(ActionCodeUseCase actionCodes) {
        this.actionCodes = actionCodes;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CodeSent request(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ActionCodeRequests.RequestCode body,
                            HttpServletRequest request) {
        return actionCodes.requestCode(UUID.fromString(jwt.getSubject()), body.action(), RequestContexts.from(request));
    }

    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ActionCodeRequests.VerifyCode body,
                       HttpServletRequest request) {
        actionCodes.verifyCode(UUID.fromString(jwt.getSubject()), body.action(), body.code(), RequestContexts.from(request));
    }
}
