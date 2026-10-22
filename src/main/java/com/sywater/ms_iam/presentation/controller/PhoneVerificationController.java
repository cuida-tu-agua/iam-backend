package com.sywater.ms_iam.presentation.controller;

import com.sywater.ms_iam.application.dto.PhoneCodeSent;
import com.sywater.ms_iam.application.dto.UserView;
import com.sywater.ms_iam.application.port.in.PhoneVerificationUseCase;
import com.sywater.ms_iam.presentation.dto.PhoneRequests;
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

/**
 * HU-028: verify the phone by SMS or WhatsApp. 201 code sent · 400 bad country / number / mode · 409 the number belongs to
 * another account · 422 phone.not_reachable (the number does not work for that mode) · 429 asked again too soon ·
 * 503 the SMS service is down. Confirm: 200 the profile with phoneVerified + phoneChannel · 400 wrong / expired code.
 */
@RestController
@RequestMapping("/api/users/me/phone-verification")
public class PhoneVerificationController {

    private final PhoneVerificationUseCase phones;

    public PhoneVerificationController(PhoneVerificationUseCase phones) {
        this.phones = phones;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PhoneCodeSent request(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PhoneRequests.RequestCode body,
                                 HttpServletRequest request) {
        return phones.requestCode(UUID.fromString(jwt.getSubject()), body.country(), body.phone(), body.channel(),
                RequestContexts.from(request));
    }

    @PostMapping("/confirm")
    public UserView confirm(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PhoneRequests.Confirm body,
                            HttpServletRequest request) {
        return phones.confirm(UUID.fromString(jwt.getSubject()), body.code(), RequestContexts.from(request));
    }
}
