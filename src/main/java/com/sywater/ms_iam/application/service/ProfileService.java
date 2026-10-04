package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.AuthSettings;
import com.sywater.ms_iam.application.dto.RequestContext;
import com.sywater.ms_iam.application.dto.UserView;
import com.sywater.ms_iam.application.port.in.ProfileUseCase;
import com.sywater.ms_iam.application.port.out.ActivityLog;
import com.sywater.ms_iam.application.port.out.AvatarStorage;
import com.sywater.ms_iam.application.port.out.CredentialRepository;
import com.sywater.ms_iam.application.port.out.NotificationSender;
import com.sywater.ms_iam.application.port.out.PasswordHasher;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.CurrentPasswordIncorrectException;
import com.sywater.ms_iam.domain.exception.InvalidAvatarException;
import com.sywater.ms_iam.domain.exception.PhoneAlreadyRegisteredException;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import com.sywater.ms_iam.domain.model.PasswordPolicy;
import com.sywater.ms_iam.domain.model.PhoneNumber;
import com.sywater.ms_iam.domain.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class ProfileService implements ProfileUseCase {

    private final UserRepository users;
    private final CredentialRepository credentials;
    private final PasswordHasher passwordHasher;
    private final AvatarStorage avatars;
    private final NotificationSender notifications;
    private final ActivityLog activity;
    private final AuthSettings settings;
    private final Clock clock;

    public ProfileService(UserRepository users, CredentialRepository credentials, PasswordHasher passwordHasher,
                          AvatarStorage avatars, NotificationSender notifications, ActivityLog activity,
                          AuthSettings settings, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.passwordHasher = passwordHasher;
        this.avatars = avatars;
        this.notifications = notifications;
        this.activity = activity;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserView getProfile(UUID userId) {
        return UserView.from(load(userId));
    }

    @Override
    @Transactional
    public UserView updateProfile(UUID userId, UpdateCommand command, RequestContext context) {
        Instant now = clock.instant();
        User user = load(userId);
        PhoneNumber phone = PhoneNumber.ofNullable(command.phone());
        if (phone != null && users.phoneTakenByOther(phone, userId)) throw new PhoneAlreadyRegisteredException();

        user.updateProfile(command.firstName(), command.lastName(), phone, now);
        users.update(user);
        activity.record(userId, "PROFILE_UPDATED", Map.of(), context.ip(), now);
        return UserView.from(user);
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, RequestContext context) {
        Instant now = clock.instant();
        User user = load(userId);
        String hash = credentials.findPasswordHash(userId).orElseThrow(CurrentPasswordIncorrectException::new);
        if (!passwordHasher.matches(currentPassword == null ? "" : currentPassword, hash)) {
            throw new CurrentPasswordIncorrectException();
        }
        PasswordPolicy.validate(newPassword);

        credentials.savePasswordHash(userId, passwordHasher.hash(newPassword));
        activity.record(userId, "PASSWORD_CHANGED", Map.of(), context.ip(), now);
        notifications.sendPasswordChanged(user.email(), user.firstName());
    }

    @Override
    @Transactional
    public UserView changeAvatar(UUID userId, byte[] content, String contentType, RequestContext context) {
        Instant now = clock.instant();
        User user = load(userId);
        String extension = validateImage(content, contentType);

        String previous = user.avatarUrl();
        user.changeAvatar(avatars.store(userId, content, extension), now);
        users.update(user);
        if (previous != null) avatars.delete(previous);

        activity.record(userId, "AVATAR_CHANGED", Map.of(), context.ip(), now);
        return UserView.from(user);
    }

    @Override
    @Transactional
    public UserView removeAvatar(UUID userId, RequestContext context) {
        Instant now = clock.instant();
        User user = load(userId);
        String previous = user.avatarUrl();
        user.changeAvatar(null, now);
        users.update(user);
        if (previous != null) avatars.delete(previous);
        return UserView.from(user);
    }

    private User load(UUID userId) {
        return users.findById(userId).filter(u -> !u.isDeleted()).orElseThrow(UserNotFoundException::new);
    }

    private String validateImage(byte[] content, String contentType) {
        if (content == null || content.length == 0) throw new InvalidAvatarException("The photo is empty.");
        if (content.length > settings.avatarMaxBytes()) {
            throw new InvalidAvatarException("The photo must be at most " + settings.avatarMaxBytes() / 1024 + " KB.");
        }
        boolean jpeg = content.length > 3 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF;
        boolean png = content.length > 8 && (content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G';
        if (jpeg && (contentType == null || contentType.equals("image/jpeg"))) return "jpg";
        if (png && (contentType == null || contentType.equals("image/png"))) return "png";
        throw new InvalidAvatarException("The photo must be a JPEG or PNG image.");
    }
}
