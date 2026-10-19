package com.sywater.ms_iam.presentation.controller;

import com.sywater.ms_iam.application.dto.AuthResult;
import com.sywater.ms_iam.application.port.in.GoogleSignInUseCase;
import com.sywater.ms_iam.presentation.dto.AuthRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * HU-004 "Continuar con Google" (OAuth 2.0 authorization code + PKCE). The browser / phone only sees redirects and,
 * at the end, a single-use code it trades for the normal session.
 */
@RestController
@RequestMapping("/api/auth/google")
public class GoogleAuthController {

    private final GoogleSignInUseCase google;

    public GoogleAuthController(GoogleSignInUseCase google) {
        this.google = google;
    }

    /** The app asks whether to show the button. */
    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("enabled", google.enabled());
    }

    /** Opened by the app (web: same tab; phone: in-app browser). 302 to Google's consent page. */
    @GetMapping("/start")
    public ResponseEntity<Void> start(@RequestParam("redirect") String redirect) {
        return redirectTo(google.start(redirect));
    }

    /** Google comes back here (the "redirect URI" registered in Google Cloud). 302 to the app. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state,
                                         @RequestParam(value = "error", required = false) String error,
                                         HttpServletRequest request) {
        return redirectTo(google.finish(code, state, error, RequestContexts.from(request)));
    }

    /** The app trades the "google_code" it received for its session (same body as POST /api/auth/login). */
    @PostMapping("/exchange")
    public AuthResult exchange(@Valid @RequestBody AuthRequests.GoogleExchange body, HttpServletRequest request) {
        return google.exchange(body.code(), RequestContexts.from(request));
    }

    private static ResponseEntity<Void> redirectTo(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(location)
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
