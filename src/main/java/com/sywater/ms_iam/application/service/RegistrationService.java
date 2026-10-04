package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.AuthSettings;
import com.sywater.ms_iam.application.dto.CodeSent;
import com.sywater.ms_iam.application.port.in.EmailVerificationUseCase;
import com.sywater.ms_iam.application.port.in.RegisterUserUseCase;
import com.sywater.ms_iam.application.port.out.ActivityLog;
import com.sywater.ms_iam.application.port.out.CredentialRepository;
import com.sywater.ms_iam.application.port.out.NotificationSender;
import com.sywater.ms_iam.application.port.out.OneTimeCodeRepository.Purpose;
import com.sywater.ms_iam.application.port.out.PasswordHasher;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.AccountNotFoundException;
import com.sywater.ms_iam.domain.exception.AlreadyVerifiedException;
import com.sywater.ms_iam.domain.exception.DomainException;
import com.sywater.ms_iam.domain.exception.EmailAlreadyRegisteredException;
import com.sywater.ms_iam.domain.exception.PhoneAlreadyRegisteredException;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.PasswordPolicy;
import com.sywater.ms_iam.domain.model.PhoneNumber;
import com.sywater.ms_iam.domain.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

public class RegistrationService implements RegisterUserUseCase, EmailVerificationUseCase {

    private final UserRepository users;
    private final CredentialRepository credentials;
    private final PasswordHasher passwordHasher;
    private final OneTimeCodes codes;
    private final NotificationSender notifications;
    private final ActivityLog activity;
    private final AuthSettings settings;
    private final Clock clock;

    public RegistrationService(UserRepository users, CredentialRepository credentials, PasswordHasher passwordHasher,
                               OneTimeCodes codes, NotificationSender notifications, ActivityLog activity,
                               AuthSettings settings, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.passwordHasher = passwordHasher;
        this.codes = codes;
        this.notifications = notifications;
        this.activity = activity;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CodeSent register(Command command) {
        Instant now = clock.instant();

        Email email = new Email(command.email());
        PhoneNumber phone = PhoneNumber.ofNullable(command.phone());
        User user = User.register(command.firstName(), command.lastName(), email, phone, now);
        PasswordPolicy.validate(command.password());

        if (users.existsByEmail(email)) throw new EmailAlreadyRegisteredException();
        if (phone != null && users.phoneTakenByOther(phone, null)) throw new PhoneAlreadyRegisteredException();

        users.create(user);
        credentials.savePasswordHash(user.id(), passwordHasher.hash(command.password()));

        String code = codes.issue(Purpose.EMAIL_VERIFICATION, user.id(), settings.verificationCodeTtl(), now, false);
        notifications.sendVerificationCode(email, user.firstName(), code, settings.verificationCodeTtl());
        activity.record(user.id(), "REGISTERED", Map.of(), null, now);

        return new CodeSent(email.masked(), now.plus(settings.verificationCodeTtl()));
    }

    @Override
    @Transactional(noRollbackFor = DomainException.class)
    public void verify(String rawEmail, String code) {
        Instant now = clock.instant();
        User user = findActiveUser(rawEmail);
        if (user.isEmailVerified()) throw new AlreadyVerifiedException();

        codes.verify(Purpose.EMAIL_VERIFICATION, user.id(), code, now);
        user.verifyEmail(now);
        users.update(user);
        activity.record(user.id(), "EMAIL_VERIFIED", Map.of(), null, now);
    }

    @Override
    @Transactional
    public CodeSent resend(String rawEmail) {
        Instant now = clock.instant();
        User user = findActiveUser(rawEmail);
        if (user.isEmailVerified()) throw new AlreadyVerifiedException();

        String code = codes.issue(Purpose.EMAIL_VERIFICATION, user.id(), settings.verificationCodeTtl(), now, true);
        notifications.sendVerificationCode(user.email(), user.firstName(), code, settings.verificationCodeTtl());
        return new CodeSent(user.email().masked(), now.plus(settings.verificationCodeTtl()));
    }

    private User findActiveUser(String rawEmail) {
        return users.findByEmail(new Email(rawEmail))
                .filter(u -> !u.isDeleted())
                .orElseThrow(AccountNotFoundException::new);
    }
}
