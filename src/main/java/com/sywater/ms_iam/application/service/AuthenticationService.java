package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.AuthResult;
import com.sywater.ms_iam.application.dto.AuthSettings;
import com.sywater.ms_iam.application.dto.RequestContext;
import com.sywater.ms_iam.application.port.in.LoginUseCase;
import com.sywater.ms_iam.application.port.in.SessionUseCase;
import com.sywater.ms_iam.application.port.out.ActivityLog;
import com.sywater.ms_iam.application.port.out.CredentialRepository;
import com.sywater.ms_iam.application.port.out.LoginAttemptRepository;
import com.sywater.ms_iam.application.port.out.PasswordHasher;
import com.sywater.ms_iam.application.port.out.RefreshTokenRepository;
import com.sywater.ms_iam.application.port.out.RefreshTokenRepository.StoredRefreshToken;
import com.sywater.ms_iam.application.port.out.SecretHasher;
import com.sywater.ms_iam.application.port.out.TokenRevocationStore;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.AccountBlockedException;
import com.sywater.ms_iam.domain.exception.AccountLockedException;
import com.sywater.ms_iam.domain.exception.AccountNotVerifiedException;
import com.sywater.ms_iam.domain.exception.DomainException;
import com.sywater.ms_iam.domain.exception.EmailNotFoundException;
import com.sywater.ms_iam.domain.exception.InvalidRefreshTokenException;
import com.sywater.ms_iam.domain.exception.WrongPasswordException;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.LockoutPolicy;
import com.sywater.ms_iam.domain.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


public class AuthenticationService implements LoginUseCase, SessionUseCase {

    private final UserRepository users;
    private final CredentialRepository credentials;
    private final PasswordHasher passwordHasher;
    private final LoginAttemptRepository attempts;
    private final RefreshTokenRepository refreshTokens;
    private final SecretHasher secretHasher;
    private final TokenRevocationStore revocations;
    private final SessionIssuer sessions;
    private final ActivityLog activity;
    private final LockoutPolicy lockout;
    private final Clock clock;

    public AuthenticationService(UserRepository users, CredentialRepository credentials, PasswordHasher passwordHasher,
                                 LoginAttemptRepository attempts, RefreshTokenRepository refreshTokens,
                                 SecretHasher secretHasher, TokenRevocationStore revocations, SessionIssuer sessions,
                                 ActivityLog activity, AuthSettings settings, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.passwordHasher = passwordHasher;
        this.attempts = attempts;
        this.refreshTokens = refreshTokens;
        this.secretHasher = secretHasher;
        this.revocations = revocations;
        this.sessions = sessions;
        this.activity = activity;
        this.lockout = settings.lockout();
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = DomainException.class)
    public AuthResult login(String rawEmail, String password, RequestContext context) {
        Instant now = clock.instant();
        Email email = new Email(rawEmail);

        Optional<User> found = users.findByEmail(email).filter(u -> !u.isDeleted());
        if (found.isEmpty()) {
            attempts.record(null, email.value(), context.ip(), false, "EMAIL_NOT_FOUND", now);
            throw new EmailNotFoundException();
        }
        User user = found.get();

        try {
            user.ensureCanAttemptLogin(now);
        } catch (AccountLockedException | AccountBlockedException e) {
            attempts.record(user.id(), email.value(), context.ip(), false,
                    e instanceof AccountLockedException ? "ACCOUNT_LOCKED" : "ACCOUNT_BLOCKED", now);
            throw e;
        }

        String hash = credentials.findPasswordHash(user.id()).orElse(null);
        if (hash == null || !passwordHasher.matches(password == null ? "" : password, hash)) {
            attempts.record(user.id(), email.value(), context.ip(), false, "WRONG_PASSWORD", now);
            Instant since = lockout.countFailuresSince(now, attempts.lastSuccessAt(user.id()).orElse(null),
                    user.accountLockedUntil());
            long failures = attempts.countFailuresSince(user.id(), since);

            if (lockout.shouldLock(failures)) {
                Instant until = now.plus(lockout.duration());
                user.lockUntil(until, now);
                users.update(user);
                activity.record(user.id(), "ACCOUNT_LOCKED", Map.of("until", until.toString()), context.ip(), now);
                throw new AccountLockedException(until);
            }
            throw new WrongPasswordException(lockout.remainingAttempts(failures));
        }

        try {
            user.ensureVerified();
        } catch (AccountNotVerifiedException e) {
            attempts.record(user.id(), email.value(), context.ip(), false, "ACCOUNT_NOT_VERIFIED", now);
            throw e;
        }

        attempts.record(user.id(), email.value(), context.ip(), true, null, now);
        activity.record(user.id(), "LOGIN", Map.of(), context.ip(), now);
        return sessions.open(user, context, now);
    }

    @Override
    @Transactional(noRollbackFor = DomainException.class)
    public AuthResult refresh(String refreshToken, RequestContext context) {
        Instant now = clock.instant();
        if (refreshToken == null || refreshToken.isBlank()) throw new InvalidRefreshTokenException();

        StoredRefreshToken stored = refreshTokens.findByHash(secretHasher.hash(refreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (stored.revokedAt() != null) {
            refreshTokens.revokeAllForUser(stored.userId(), now);
            revocations.revokeAllIssuedBefore(stored.userId(), now);
            activity.record(stored.userId(), "REFRESH_TOKEN_REUSED", Map.of(), context.ip(), now);
            throw new InvalidRefreshTokenException();
        }
        if (!stored.expiresAt().isAfter(now)) throw new InvalidRefreshTokenException();

        User user = users.findById(stored.userId())
                .filter(u -> !u.isDeleted())
                .orElseThrow(InvalidRefreshTokenException::new);
        if (user.blockedAt() != null) throw new AccountBlockedException();

        if (!refreshTokens.revokeIfActive(stored.id(), now)) throw new InvalidRefreshTokenException();

        return sessions.open(user, context, now);
    }

    @Override
    @Transactional
    public void logout(UUID userId, String accessTokenId, Instant accessTokenExpiresAt, String refreshToken,
                       RequestContext context) {
        Instant now = clock.instant();

        if (accessTokenId != null && accessTokenExpiresAt != null) {
            revocations.revokeToken(accessTokenId, accessTokenExpiresAt);
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokens.findByHash(secretHasher.hash(refreshToken))
                    .filter(t -> t.userId().equals(userId))
                    .ifPresent(t -> refreshTokens.revokeIfActive(t.id(), now));
        }
        activity.record(userId, "LOGOUT", Map.of(), context.ip(), now);
    }
}
