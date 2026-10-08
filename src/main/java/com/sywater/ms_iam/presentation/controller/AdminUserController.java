package com.sywater.ms_iam.presentation.controller;

import com.sywater.ms_iam.application.dto.AdminUserView;
import com.sywater.ms_iam.application.dto.PageView;
import com.sywater.ms_iam.application.port.in.BlockUserUseCase;
import com.sywater.ms_iam.application.port.in.ListUsersUseCase;
import com.sywater.ms_iam.domain.model.AccountStatus;
import com.sywater.ms_iam.presentation.dto.AdminRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ListUsersUseCase listing;

    public AdminUserController(BlockUserUseCase blocking, ListUsersUseCase listing) {
        this.blocking = blocking;
        this.listing = listing;
    }

    /**
     * HU-059: registered users, newest first. search = part of the name or e-mail; status = ACTIVE | BLOCKED |
     * UNVERIFIED (nothing = all); page starts at 0, size 20 by default (max 100).
     */
    @GetMapping
    public PageView<AdminUserView> list(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam(required = false) String search,
                                        @RequestParam(required = false) AccountStatus status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return listing.list(admin(jwt), search, status, page, size);
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
