package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.AdminUserView;
import com.sywater.ms_iam.application.dto.RequestContext;
import com.sywater.ms_iam.application.port.in.BlockUserUseCase;
import com.sywater.ms_iam.application.port.out.ActivityLog;
import com.sywater.ms_iam.application.port.out.RefreshTokenRepository;
import com.sywater.ms_iam.application.port.out.TokenRevocationStore;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.NotAdministratorException;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import com.sywater.ms_iam.domain.model.Role;
import com.sywater.ms_iam.domain.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UserBlockingService implements BlockUserUseCase {

    static final int REASON_MAX = 200;

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final TokenRevocationStore revocations;
    private final ActivityLog activity;
    private final Clock clock;

    public UserBlockingService(UserRepository users, RefreshTokenRepository refreshTokens,
                               TokenRevocationStore revocations, ActivityLog activity, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.revocations = revocations;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AdminUserView block(UUID administratorId, UUID userId, String reason, RequestContext context) {
        Instant now = clock.instant();
        requireAdministrator(administratorId);
        User user = users.findById(userId).filter(u -> !u.isDeleted()).orElseThrow(UserNotFoundException::new);

        if (user.block(administratorId, now)) {
            users.update(user);
            // The account must be unreachable NOW, not when its tokens expire (up to 1 h / 7 days)
            refreshTokens.revokeAllForUser(userId, now);
            revocations.revokeAllIssuedBefore(userId, now);
            audit("USER_BLOCKED", "ACCOUNT_BLOCKED", administratorId, userId, reason, context, now);
        }
        return AdminUserView.of(user);
    }

    @Override
    @Transactional
    public AdminUserView unblock(UUID administratorId, UUID userId, String reason, RequestContext context) {
        Instant now = clock.instant();
        requireAdministrator(administratorId);
        User user = users.findById(userId).filter(u -> !u.isDeleted()).orElseThrow(UserNotFoundException::new);

        if (user.unblock(now)) {
            users.update(user);
            audit("USER_UNBLOCKED", "ACCOUNT_UNBLOCKED", administratorId, userId, reason, context, now);
        }
        return AdminUserView.of(user);
    }

    /** The token says ADMIN, but the database has the last word: a demoted or blocked admin loses the power at once. */
    private void requireAdministrator(UUID administratorId) {
        User admin = users.findById(administratorId).filter(u -> !u.isDeleted() && !u.isBlocked())
                .orElseThrow(NotAdministratorException::new);
        if (!admin.hasRole(Role.ADMIN)) throw new NotAdministratorException();
    }

    /** Two rows: the action of the administrator (who did it) and the history of the user (what happened to them). */
    private void audit(String adminAction, String userAction, UUID administratorId, UUID userId, String reason,
                       RequestContext context, Instant now) {
        Map<String, String> byAdmin = new HashMap<>(Map.of("targetUserId", userId.toString()));
        Map<String, String> onUser = new HashMap<>(Map.of("administratorId", administratorId.toString()));
        if (reason != null && !reason.isBlank()) {
            String note = reason.trim().length() > REASON_MAX ? reason.trim().substring(0, REASON_MAX) : reason.trim();
            byAdmin.put("reason", note);
            onUser.put("reason", note);
        }
        activity.record(administratorId, adminAction, byAdmin, context.ip(), now);
        activity.record(userId, userAction, onUser, context.ip(), now);
    }
}
