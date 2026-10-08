package com.sywater.ms_iam.presentation.controller;

import com.sywater.ms_iam.application.dto.PlatformMetricsView;
import com.sywater.ms_iam.application.port.in.PlatformMetricsUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** E14 · HU-062. SecurityConfig only lets the ADMIN role in; the use case checks it again in the database. */
@RestController
@RequestMapping("/api/admin/metrics")
public class AdminMetricsController {

    private final PlatformMetricsUseCase metrics;

    public AdminMetricsController(PlatformMetricsUseCase metrics) {
        this.metrics = metrics;
    }

    /** Calculated on every call, so the screen shows fresh numbers each time it loads. */
    @GetMapping
    public PlatformMetricsView get(@AuthenticationPrincipal Jwt jwt) {
        return metrics.get(UUID.fromString(jwt.getSubject()));
    }
}
