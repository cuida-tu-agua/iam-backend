package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.CodeSent;
import com.sywater.ms_iam.application.dto.RequestContext;
import com.sywater.ms_iam.application.port.in.ActionCodeUseCase;
import com.sywater.ms_iam.application.port.out.ActivityLog;
import com.sywater.ms_iam.application.port.out.NotificationSender;
import com.sywater.ms_iam.application.port.out.OneTimeCodeRepository.Purpose;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.AccountBlockedException;
import com.sywater.ms_iam.domain.exception.DomainException;
import com.sywater.ms_iam.domain.exception.UnknownActionException;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import com.sywater.ms_iam.domain.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class ActionCodeService implements ActionCodeUseCase {

    private final UserRepository users;
    private final OneTimeCodes codes;
    private final NotificationSender notifications;
    private final ActivityLog activity;
    private final Duration validFor;
    private final Clock clock;

    public ActionCodeService(UserRepository users, OneTimeCodes codes, NotificationSender notifications,
                             ActivityLog activity, Duration validFor, Clock clock) {
        this.users = users;
        this.codes = codes;
        this.notifications = notifications;
        this.activity = activity;
        this.validFor = validFor;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CodeSent requestCode(UUID userId, String action, RequestContext context) {
        Action parsed = parse(action);
        Instant now = clock.instant();
        User user = activeUser(userId);

        String code = codes.issue(purposeOf(parsed), user.id(), validFor, now, true);
        notifications.sendActionCode(user.email(), user.firstName(), describe(parsed), code, validFor);
        activity.record(user.id(), "ACTION_CODE_REQUESTED", Map.of("action", parsed.name()), context.ip(), now);
        return new CodeSent(user.email().masked(), now.plus(validFor));
    }

    @Override
    @Transactional(noRollbackFor = DomainException.class)
    public void verifyCode(UUID userId, String action, String code, RequestContext context) {
        Action parsed = parse(action);
        Instant now = clock.instant();
        User user = activeUser(userId);

        codes.verify(purposeOf(parsed), user.id(), code, now);
        activity.record(user.id(), "ACTION_CODE_VERIFIED", Map.of("action", parsed.name()), context.ip(), now);
    }

    private User activeUser(UUID userId) {
        User user = users.findById(userId).filter(u -> !u.isDeleted()).orElseThrow(UserNotFoundException::new);
        if (user.blockedAt() != null) throw new AccountBlockedException();
        return user;
    }

    private static Action parse(String action) {
        try {
            return Action.valueOf(action == null ? "" : action.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UnknownActionException(action);
        }
    }

    private static Purpose purposeOf(Action action) {
        return switch (action) {
            case VALVE_CLOSE -> Purpose.VALVE_CLOSE;
        };
    }

    private static String describe(Action action) {
        return switch (action) {
            case VALVE_CLOSE -> "cerrar la válvula de agua";
        };
    }
}
