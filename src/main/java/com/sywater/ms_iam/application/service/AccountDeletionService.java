package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.RequestContext;
import com.sywater.ms_iam.application.port.in.DeleteAccountUseCase;
import com.sywater.ms_iam.application.port.out.ActivityLog;
import com.sywater.ms_iam.application.port.out.AvatarStorage;
import com.sywater.ms_iam.application.port.out.CredentialRepository;
import com.sywater.ms_iam.application.port.out.DeviceCleanup;
import com.sywater.ms_iam.application.port.out.PasswordHasher;
import com.sywater.ms_iam.application.port.out.RefreshTokenRepository;
import com.sywater.ms_iam.application.port.out.TokenRevocationStore;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.CurrentPasswordIncorrectException;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import com.sywater.ms_iam.domain.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class AccountDeletionService implements DeleteAccountUseCase {

    private final UserRepository users;
    private final CredentialRepository credentials;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenRepository refreshTokens;
    private final TokenRevocationStore revocations;
    private final AvatarStorage avatars;
    private final DeviceCleanup devices;
    private final ActivityLog activity;
    private final Clock clock;

    public AccountDeletionService(UserRepository users, CredentialRepository credentials, PasswordHasher passwordHasher,
                                  RefreshTokenRepository refreshTokens, TokenRevocationStore revocations,
                                  AvatarStorage avatars, DeviceCleanup devices, ActivityLog activity, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.passwordHasher = passwordHasher;
        this.refreshTokens = refreshTokens;
        this.revocations = revocations;
        this.avatars = avatars;
        this.devices = devices;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void deleteAccount(UUID userId, String password, RequestContext context) {
        Instant now = clock.instant();
        User user = users.findById(userId).filter(u -> !u.isDeleted()).orElseThrow(UserNotFoundException::new);

        String hash = credentials.findPasswordHash(userId).orElseThrow(CurrentPasswordIncorrectException::new);
        if (!passwordHasher.matches(password == null ? "" : password, hash)) throw new CurrentPasswordIncorrectException();

        devices.unlinkAllDevicesOf(userId);
        refreshTokens.revokeAllForUser(userId, now);
        revocations.revokeAllIssuedBefore(userId, now);

        String avatar = user.avatarUrl();
        user.delete(now);
        users.update(user);
        if (avatar != null) avatars.delete(avatar);

        activity.record(userId, "ACCOUNT_DELETED", Map.of(), context.ip(), now);
    }
}
