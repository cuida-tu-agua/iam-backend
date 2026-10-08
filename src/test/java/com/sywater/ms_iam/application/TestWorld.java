package com.sywater.ms_iam.application;

import com.sywater.ms_iam.application.dto.AuthSettings;
import com.sywater.ms_iam.application.dto.RequestContext;
import com.sywater.ms_iam.application.port.in.RegisterUserUseCase;
import com.sywater.ms_iam.application.service.*;
import com.sywater.ms_iam.domain.model.LockoutPolicy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public final class TestWorld {

    public static final String PASSWORD = "Agua2026!";
    public static final RequestContext CTX = new RequestContext("10.0.0.7", "jest");

    public Instant now = Instant.parse("2026-09-29T15:00:00Z");
    public final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    };

    public final Fakes.Users users = new Fakes.Users();
    public final Fakes.Credentials credentials = new Fakes.Credentials();
    public final Fakes.Hasher hasher = new Fakes.Hasher();
    public final Fakes.Generator generator = new Fakes.Generator();
    public final Fakes.Codes codes = new Fakes.Codes();
    public final Fakes.RefreshTokens refreshTokens = new Fakes.RefreshTokens();
    public final Fakes.Attempts attempts = new Fakes.Attempts();
    public final Fakes.Activity activity = new Fakes.Activity();
    public final Fakes.Tokens tokens = new Fakes.Tokens();
    public final Fakes.Revocations revocations = new Fakes.Revocations();
    public final Fakes.Mailbox mailbox = new Fakes.Mailbox();
    public final Fakes.Avatars avatars = new Fakes.Avatars();
    public final Fakes.Devices devices = new Fakes.Devices();


    public final AuthSettings settings = new AuthSettings(Duration.ofHours(1), Duration.ofDays(7),
            Duration.ofHours(24), Duration.ofMinutes(15), 5, Duration.ofSeconds(60),
            new LockoutPolicy(5, Duration.ofMinutes(15)), 2 * 1024 * 1024);

    private final OneTimeCodes oneTimeCodes = new OneTimeCodes(codes, generator, hasher, settings);
    private final SessionIssuer sessions = new SessionIssuer(tokens, refreshTokens, generator, hasher, settings);

    public final RegistrationService registration = new RegistrationService(users, credentials, hasher, oneTimeCodes,
            mailbox, activity, settings, clock);
    public final AuthenticationService auth = new AuthenticationService(users, credentials, hasher, attempts,
            refreshTokens, hasher, revocations, sessions, activity, settings, clock);
    public final PasswordRecoveryService recovery = new PasswordRecoveryService(users, credentials, hasher,
            oneTimeCodes, refreshTokens, revocations, mailbox, activity, settings, clock);
    public final ProfileService profile = new ProfileService(users, credentials, hasher, avatars, mailbox, activity,
            settings, clock);
    public final AccountDeletionService deletion = new AccountDeletionService(users, credentials, hasher,
            refreshTokens, revocations, avatars, devices, activity, clock);

    public final UserBlockingService blocking = new UserBlockingService(users, refreshTokens, revocations, activity, clock);

    public final ActionCodeService actionCodes = new ActionCodeService(users, oneTimeCodes, mailbox, activity,
            Duration.ofMinutes(5), clock);

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    /** Registers and verifies an account; returns its id. */
    public UUID verifiedUser(String email) {
        registration.register(new RegisterUserUseCase.Command("Juan", "Ome", email, null, PASSWORD));
        registration.verify(email, mailbox.lastCode());
        return users.findByEmail(new com.sywater.ms_iam.domain.model.Email(email)).orElseThrow().id();
    }
}
