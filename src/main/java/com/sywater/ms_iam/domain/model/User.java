package com.sywater.ms_iam.domain.model;

import com.sywater.ms_iam.domain.exception.AccountBlockedException;
import com.sywater.ms_iam.domain.exception.AccountLockedException;
import com.sywater.ms_iam.domain.exception.AccountNotVerifiedException;
import com.sywater.ms_iam.domain.exception.AlreadyVerifiedException;
import com.sywater.ms_iam.domain.exception.EmailNotFoundException;
import com.sywater.ms_iam.domain.exception.InvalidUserDataException;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class User {

    public static final int NAME_MIN = 2;
    public static final int NAME_MAX = 100;
    public static final int AVATAR_URL_MAX = 500;

    // Letters of any language (á, ñ, ü...), spaces, apostrophes, dots and hyphens.
    private static final Pattern NAME_FORMAT = Pattern.compile("^[\\p{L}][\\p{L} '.-]*$");

    private final UUID id;
    private String firstName;
    private String lastName;
    private Email email;
    private PhoneNumber phone;
    private String avatarUrl;
    private boolean emailVerified;
    private Instant accountLockedUntil;
    private final Instant blockedAt;
    private Instant deletedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private final Set<Role> roles;

    private User(UUID id, String firstName, String lastName, Email email, PhoneNumber phone, String avatarUrl,
                 boolean emailVerified, Instant accountLockedUntil, Instant blockedAt, Instant deletedAt,
                 Instant createdAt, Instant updatedAt, Set<Role> roles) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.avatarUrl = avatarUrl;
        this.emailVerified = emailVerified;
        this.accountLockedUntil = accountLockedUntil;
        this.blockedAt = blockedAt;
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
    }

    /**  a new account starts unverified and with the USER role. */
    public static User register(String firstName, String lastName, Email email, PhoneNumber phone, Instant now) {
        return new User(UUID.randomUUID(), cleanName(firstName, "first name"), cleanName(lastName, "last name"),
                email, phone, null, false, null, null, null, now, now, EnumSet.of(Role.USER));
    }

    public static User restore(UUID id, String firstName, String lastName, Email email, PhoneNumber phone,
                               String avatarUrl, boolean emailVerified, Instant accountLockedUntil,
                               Instant blockedAt, Instant deletedAt, Instant createdAt, Instant updatedAt,
                               Set<Role> roles) {
        return new User(id, firstName, lastName, email, phone, avatarUrl, emailVerified, accountLockedUntil,
                blockedAt, deletedAt, createdAt, updatedAt, roles);
    }


    public void ensureCanAttemptLogin(Instant now) {
        if (isDeleted()) throw new EmailNotFoundException();
        if (blockedAt != null) throw new AccountBlockedException();
        if (isLockedAt(now)) throw new AccountLockedException(accountLockedUntil);
    }

    public void ensureVerified() {
        if (!emailVerified) throw new AccountNotVerifiedException();
    }

    public boolean isLockedAt(Instant now) {
        return accountLockedUntil != null && accountLockedUntil.isAfter(now);
    }

    public void lockUntil(Instant until, Instant now) {
        this.accountLockedUntil = until;
        this.updatedAt = now;
    }

    public void verifyEmail(Instant now) {
        if (emailVerified) throw new AlreadyVerifiedException();
        this.emailVerified = true;
        this.updatedAt = now;
    }

    public void updateProfile(String firstName, String lastName, PhoneNumber phone, Instant now) {
        this.firstName = cleanName(firstName, "first name");
        this.lastName = cleanName(lastName, "last name");
        this.phone = phone;
        this.updatedAt = now;
    }

    public void changeAvatar(String url, Instant now) {
        if (url != null && url.length() > AVATAR_URL_MAX) throw new InvalidUserDataException("The photo URL is too long.");
        this.avatarUrl = url;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
        this.firstName = "Usuario";
        this.lastName = "Eliminado";
        this.email = new Email("deleted-" + id + "@deleted.invalid");
        this.phone = null;
        this.avatarUrl = null;
        this.updatedAt = now;
    }

    private static String cleanName(String raw, String field) {
        String value = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (value.length() < NAME_MIN || value.length() > NAME_MAX) {
            throw new InvalidUserDataException("The " + field + " must have " + NAME_MIN + " to " + NAME_MAX + " characters.");
        }
        if (!NAME_FORMAT.matcher(value).matches()) {
            throw new InvalidUserDataException("The " + field + " can only have letters, spaces, apostrophes and hyphens.");
        }
        return value;
    }


    public UUID id() { return id; }
    public String firstName() { return firstName; }
    public String lastName() { return lastName; }
    public String fullName() { return firstName + " " + lastName; }
    public Email email() { return email; }
    public PhoneNumber phone() { return phone; }
    public String avatarUrl() { return avatarUrl; }
    public boolean isEmailVerified() { return emailVerified; }
    public Instant accountLockedUntil() { return accountLockedUntil; }
    public Instant blockedAt() { return blockedAt; }
    public Instant deletedAt() { return deletedAt; }
    public boolean isDeleted() { return deletedAt != null; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Set<Role> roles() { return Collections.unmodifiableSet(roles); }
}
