package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.AuthSettings;
import com.sywater.ms_iam.application.dto.PhoneCodeSent;
import com.sywater.ms_iam.application.dto.RequestContext;
import com.sywater.ms_iam.application.dto.UserView;
import com.sywater.ms_iam.application.port.in.PhoneVerificationUseCase;
import com.sywater.ms_iam.application.port.out.ActivityLog;
import com.sywater.ms_iam.application.port.out.PhoneVerificationRepository;
import com.sywater.ms_iam.application.port.out.PhoneVerificationRepository.PendingCode;
import com.sywater.ms_iam.application.port.out.SecretGenerator;
import com.sywater.ms_iam.application.port.out.SecretHasher;
import com.sywater.ms_iam.application.port.out.ShortMessageGateway;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.AccountBlockedException;
import com.sywater.ms_iam.domain.exception.CodeExpiredException;
import com.sywater.ms_iam.domain.exception.CodeRecentlySentException;
import com.sywater.ms_iam.domain.exception.DomainException;
import com.sywater.ms_iam.domain.exception.InvalidCodeException;
import com.sywater.ms_iam.domain.exception.PhoneAlreadyRegisteredException;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import com.sywater.ms_iam.domain.model.PhoneChannel;
import com.sywater.ms_iam.domain.model.PhoneCountry;
import com.sywater.ms_iam.domain.model.PhoneNumber;
import com.sywater.ms_iam.domain.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * HU-028: the user picks the country, types the number and chooses SMS or WhatsApp; a 6-digit code (5 min, single use,
 * 5 tries, 60 s between requests) travels by THAT mode through ms-notification. Confirming it makes the number and the
 * mode the verified ones. A number that does not work for the mode is refused by the provider, so it can never be
 * verified with it; nothing changes on the user until the code is confirmed.
 */
public class PhoneVerificationService implements PhoneVerificationUseCase {

    private static final Pattern SIX_DIGITS = Pattern.compile("^\\d{6}$");

    private final UserRepository users;
    private final PhoneVerificationRepository codes;
    private final SecretGenerator generator;
    private final SecretHasher hasher;
    private final ShortMessageGateway messages;
    private final ActivityLog activity;
    private final AuthSettings settings;
    private final Duration validFor;
    private final Clock clock;

    public PhoneVerificationService(UserRepository users, PhoneVerificationRepository codes, SecretGenerator generator,
                                    SecretHasher hasher, ShortMessageGateway messages, ActivityLog activity,
                                    AuthSettings settings, Duration validFor, Clock clock) {
        this.users = users;
        this.codes = codes;
        this.generator = generator;
        this.hasher = hasher;
        this.messages = messages;
        this.activity = activity;
        this.settings = settings;
        this.validFor = validFor;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PhoneCodeSent requestCode(UUID userId, String country, String phone, String channel, RequestContext context) {
        Instant now = clock.instant();
        User user = activeUser(userId);
        PhoneChannel mode = PhoneChannel.parse(channel);
        PhoneNumber number = PhoneCountry.parse(country).mobile(phone);
        if (users.phoneTakenByOther(number, userId)) throw new PhoneAlreadyRegisteredException();

        codes.lastIssuedAt(userId).ifPresent(last -> {
            Duration elapsed = Duration.between(last, now);
            if (elapsed.compareTo(settings.codeResendCooldown()) < 0) {
                throw new CodeRecentlySentException(settings.codeResendCooldown().minus(elapsed));
            }
        });

        String code = generator.sixDigitCode();
        // Sent BEFORE it is stored: if the provider refuses the number or is down, no code is left behind
        messages.sendVerificationCode(number, mode, code, validFor);
        codes.issue(user.id(), hasher.hash(user.id() + ":" + code), number, mode, now.plus(validFor), now);
        activity.record(user.id(), "PHONE_CODE_REQUESTED", Map.of("channel", mode.name()), context.ip(), now);
        return new PhoneCodeSent(number.masked(), mode.name(), now.plus(validFor));
    }

    @Override
    @Transactional(noRollbackFor = DomainException.class)
    public UserView confirm(UUID userId, String rawCode, RequestContext context) {
        Instant now = clock.instant();
        User user = activeUser(userId);
        PendingCode pending = codes.findActive(userId, now)
                .filter(c -> c.failedAttempts() < settings.codeMaxAttempts())
                .orElseThrow(CodeExpiredException::new);

        String code = rawCode == null ? "" : rawCode.trim();
        if (!SIX_DIGITS.matcher(code).matches() || !hasher.matches(userId + ":" + code, pending.codeHash())) {
            int failures = codes.registerFailedAttempt(pending.id());
            throw new InvalidCodeException(Math.max(0, settings.codeMaxAttempts() - failures));
        }
        if (!codes.consume(pending.id(), now)) throw new CodeExpiredException();   // used by a parallel request
        // Another account may have verified the same number meanwhile
        if (users.phoneTakenByOther(pending.phone(), userId)) throw new PhoneAlreadyRegisteredException();

        user.verifyPhone(pending.phone(), pending.channel(), now);
        users.update(user);
        activity.record(userId, "PHONE_VERIFIED", Map.of("channel", pending.channel().name()), context.ip(), now);
        return UserView.from(user);
    }

    private User activeUser(UUID userId) {
        User user = users.findById(userId).filter(u -> !u.isDeleted()).orElseThrow(UserNotFoundException::new);
        if (user.blockedAt() != null) throw new AccountBlockedException();
        return user;
    }
}
