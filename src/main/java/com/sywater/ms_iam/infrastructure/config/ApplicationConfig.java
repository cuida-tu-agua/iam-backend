package com.sywater.ms_iam.infrastructure.config;

import com.sywater.ms_iam.application.dto.AuthSettings;
import com.sywater.ms_iam.application.port.out.AccessTokenIssuer;
import com.sywater.ms_iam.application.port.out.ActivityLog;
import com.sywater.ms_iam.application.port.out.AvatarStorage;
import com.sywater.ms_iam.application.port.out.CredentialRepository;
import com.sywater.ms_iam.application.port.out.DeviceCleanup;
import com.sywater.ms_iam.application.port.out.LoginAttemptRepository;
import com.sywater.ms_iam.application.port.out.NotificationSender;
import com.sywater.ms_iam.application.port.out.OneTimeCodeRepository;
import com.sywater.ms_iam.application.port.out.PasswordHasher;
import com.sywater.ms_iam.application.port.out.RefreshTokenRepository;
import com.sywater.ms_iam.application.port.out.SecretGenerator;
import com.sywater.ms_iam.application.port.out.SecretHasher;
import com.sywater.ms_iam.application.port.out.TokenRevocationStore;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.application.service.*;
import com.sywater.ms_iam.domain.model.LockoutPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class ApplicationConfig {

    @Bean
    AuthSettings authSettings(IamProperties p) {
        return new AuthSettings(
                p.jwt().accessTtl(), p.jwt().refreshTtl(),
                p.codes().verificationTtl(), p.codes().resetTtl(), p.codes().maxAttempts(), p.codes().resendCooldown(),
                new LockoutPolicy(p.lockout().maxAttempts(), p.lockout().duration()),
                p.avatars().maxBytes());
    }

    @Bean
    OneTimeCodes oneTimeCodes(OneTimeCodeRepository codes, SecretGenerator generator, SecretHasher hasher, AuthSettings settings) {
        return new OneTimeCodes(codes, generator, hasher, settings);
    }

    @Bean
    SessionIssuer sessionIssuer(AccessTokenIssuer accessTokens, RefreshTokenRepository refreshTokens,
                                SecretGenerator generator, SecretHasher hasher, AuthSettings settings) {
        return new SessionIssuer(accessTokens, refreshTokens, generator, hasher, settings);
    }

    @Bean
    RegistrationService registrationService(UserRepository users, CredentialRepository credentials,
                                            PasswordHasher hasher, OneTimeCodes codes, NotificationSender notifications,
                                            ActivityLog activity, AuthSettings settings, Clock clock) {
        return new RegistrationService(users, credentials, hasher, codes, notifications, activity, settings, clock);
    }

    @Bean
    AuthenticationService authenticationService(UserRepository users, CredentialRepository credentials,
                                                PasswordHasher hasher, LoginAttemptRepository attempts,
                                                RefreshTokenRepository refreshTokens, SecretHasher secretHasher,
                                                TokenRevocationStore revocations, SessionIssuer sessions,
                                                ActivityLog activity, AuthSettings settings, Clock clock) {
        return new AuthenticationService(users, credentials, hasher, attempts, refreshTokens, secretHasher,
                revocations, sessions, activity, settings, clock);
    }

    @Bean
    PasswordRecoveryService passwordRecoveryService(UserRepository users, CredentialRepository credentials,
                                                    PasswordHasher hasher, OneTimeCodes codes,
                                                    RefreshTokenRepository refreshTokens, TokenRevocationStore revocations,
                                                    NotificationSender notifications, ActivityLog activity,
                                                    AuthSettings settings, Clock clock) {
        return new PasswordRecoveryService(users, credentials, hasher, codes, refreshTokens, revocations,
                notifications, activity, settings, clock);
    }

    @Bean
    ProfileService profileService(UserRepository users, CredentialRepository credentials, PasswordHasher hasher,
                                  AvatarStorage avatars, NotificationSender notifications, ActivityLog activity,
                                  AuthSettings settings, Clock clock) {
        return new ProfileService(users, credentials, hasher, avatars, notifications, activity, settings, clock);
    }

    @Bean
    AccountDeletionService accountDeletionService(UserRepository users, CredentialRepository credentials,
                                                  PasswordHasher hasher, RefreshTokenRepository refreshTokens,
                                                  TokenRevocationStore revocations, AvatarStorage avatars,
                                                  DeviceCleanup devices, ActivityLog activity, Clock clock) {
        return new AccountDeletionService(users, credentials, hasher, refreshTokens, revocations, avatars, devices,
                activity, clock);
    }

    @Bean
    ActionCodeService actionCodeService(UserRepository users, OneTimeCodes codes, NotificationSender notifications,
                                        ActivityLog activity, @Value("${iam.codes.action-ttl:5m}") Duration validFor,
                                        Clock clock) {
        return new ActionCodeService(users, codes, notifications, activity, validFor, clock);
    }
}
