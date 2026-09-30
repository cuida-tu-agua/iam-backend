package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.AuthSettings;
import com.sywater.ms_iam.application.dto.CodeSent;
import com.sywater.ms_iam.application.dto.RequestContext;
import com.sywater.ms_iam.application.port.in.PasswordRecoveryUseCase;
import com.sywater.ms_iam.application.port.out.ActivityLog;
import com.sywater.ms_iam.application.port.out.CredentialRepository;
import com.sywater.ms_iam.application.port.out.NotificationSender;
import com.sywater.ms_iam.application.port.out.OneTimeCodeRepository.Purpose;
import com.sywater.ms_iam.application.port.out.PasswordHasher;
import com.sywater.ms_iam.application.port.out.RefreshTokenRepository;
import com.sywater.ms_iam.application.port.out.TokenRevocationStore;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.AccountBlockedException;
import com.sywater.ms_iam.domain.exception.AccountNotFoundException;
import com.sywater.ms_iam.domain.exception.DomainException;
import com.sywater.ms_iam.domain.exception.InvalidEmailException;
import com.sywater.ms_iam.domain.exception.InvalidUserDataException;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.PasswordPolicy;
import com.sywater.ms_iam.domain.model.PhoneNumber;
import com.sywater.ms_iam.domain.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public class PasswordRecoveryService implements PasswordRecoveryUseCase {

    private final UserRepository users;
    private final CredentialRepository credentials;
    private final PasswordHasher passwordHasher;
    private final OneTimeCodes codes;
    private final RefreshTokenRepository refreshTokens;
    private final TokenRevocationStore revocations;
    private final NotificationSender notifications;
    private final ActivityLog activity;
    private final AuthSettings settings;
    private final Clock clock;

    public PasswordRecoveryService(UserRepository users, CredentialRepository credentials, PasswordHasher passwordHasher,
                                   OneTimeCodes codes, RefreshTokenRepository refreshTokens,
                                   TokenRevocationStore revocations, NotificationSender notifications,
                                   ActivityLog activity, AuthSettings settings, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.passwordHasher = passwordHasher;
        this.codes = codes;
        this.refreshTokens = refreshTokens;
        this.revocations = revocations;
        this.notifications = notifications;
        this.activity = activity;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CodeSent requestReset(String emailOrPhone) {
        Instant now = clock.instant();
        User user = findAccount(emailOrPhone);
        if (user.blockedAt() != null) throw new AccountBlockedException();

        String code = codes.issue(Purpose.PASSWORD_RESET, user.id(), settings.resetCodeTtl(), now, true);
        notifications.sendPasswordResetCode(user.email(), user.firstName(), code, settings.resetCodeTtl());
        activity.record(user.id(), "PASSWORD_RESET_REQUESTED", Map.of(), null, now);
        return new CodeSent(user.email().masked(), now.plus(settings.resetCodeTtl()));
    }

    @Override
    @Transactional(noRollbackFor = DomainException.class)
    public void resetPassword(String emailOrPhone, String code, String newPassword, RequestContext context) {
        Instant now = clock.instant();
        User user = findAccount(emailOrPhone);

        PasswordPolicy.validate(newPassword);
        codes.verify(Purpose.PASSWORD_RESET, user.id(), code, now);

        credentials.savePasswordHash(user.id(), passwordHasher.hash(newPassword));

        refreshTokens.revokeAllForUser(user.id(), now);
        revocations.revokeAllIssuedBefore(user.id(), now);

        if (!user.isEmailVerified()) user.verifyEmail(now);
        user.lockUntil(now, now);
        users.update(user);

        activity.record(user.id(), "PASSWORD_RESET", Map.of(), context.ip(), now);
        notifications.sendPasswordChanged(user.email(), user.firstName());
    }

    private User findAccount(String emailOrPhone) {
        String value = emailOrPhone == null ? "" : emailOrPhone.trim();
        Optional<User> user;
        try {
            user = value.contains("@")
                    ? users.findByEmail(new Email(value))
                    : users.findByPhone(new PhoneNumber(value));
        } catch (InvalidEmailException | InvalidUserDataException e) {
            throw new AccountNotFoundException();
        }
        return user.filter(u -> !u.isDeleted()).orElseThrow(AccountNotFoundException::new);
    }
}
