package com.sywater.ms_iam.presentation.controller;

import com.sywater.ms_iam.application.dto.AdminUserView;
import com.sywater.ms_iam.application.port.in.BlockUserUseCase;
import com.sywater.ms_iam.presentation.dto.AdminRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * E14 · Admin panel. SecurityConfig only lets the ADMIN role in; the use cases check it again in the database.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final BlockUserUseCase blocking;

    public AdminUserController(BlockUserUseCase blocking) {
        this.blocking = blocking;
    }

    /** HU-060 */
    @PutMapping("/{userId}/block")
    public AdminUserView block(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
                               @Valid @RequestBody(required = false) AdminRequests.Reason body, HttpServletRequest request) {
        return blocking.block(admin(jwt), userId, body == null ? null : body.reason(), RequestContexts.from(request));
    }

    /** HU-060 */
    @PutMapping("/{userId}/unblock")
    public AdminUserView unblock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
                                 @Valid @RequestBody(required = false) AdminRequests.Reason body, HttpServletRequest request) {
        return blocking.unblock(admin(jwt), userId, body == null ? null : body.reason(), RequestContexts.from(request));
    }

    private static UUID admin(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
