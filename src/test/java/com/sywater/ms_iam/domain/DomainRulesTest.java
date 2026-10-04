package com.sywater.ms_iam.domain;

import com.sywater.ms_iam.domain.exception.*;
import com.sywater.ms_iam.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Pure domain: no Spring, no database. Runs in milliseconds. */
class DomainRulesTest {

    private static final Instant NOW = Instant.parse("2026-09-29T15:00:00Z");

    private static User newUser() {
        return User.register("Juan Esteban", "Ome", new Email("juan@mail.com"), null, NOW);
    }

    // ── Email / Phone ────────────────────────────────────────────────────

    @Test
    void email_is_trimmed_and_lowercased() {
        assertThat(new Email("  Juan.Ome@Mail.COM ").value()).isEqualTo("juan.ome@mail.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "juan", "juan@", "@mail.com", "juan@mail", "juan mail@x.com"})
    void email_rejects_bad_formats(String raw) {
        assertThatThrownBy(() -> new Email(raw)).isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void email_can_be_masked() {
        assertThat(new Email("juanome43@gmail.com").masked()).isEqualTo("ju*******@gmail.com");
        assertThat(new Email("a@b.co").masked()).isEqualTo("a*@b.co");
    }

    @Test
    void phone_ignores_spaces_and_dashes() {
        assertThat(new PhoneNumber("300 123-4567").value()).isEqualTo("3001234567");
        assertThat(new PhoneNumber("+57 (300) 123 4567").value()).isEqualTo("+573001234567");
        assertThat(PhoneNumber.ofNullable("  ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "abc1234567", "+57 300 123 4567 999 999"})
    void phone_rejects_bad_formats(String raw) {
        assertThatThrownBy(() -> new PhoneNumber(raw)).isInstanceOf(InvalidUserDataException.class);
    }

    // ── Password policy (HU-001) ─────────────────────────────────────────

    @Test
    void password_policy_accepts_the_minimum() {
        PasswordPolicy.validate("Agua2026!");
    }

    @Test
    void password_policy_lists_every_missing_rule() {
        WeakPasswordException e = catchThrowableOfType(WeakPasswordException.class, () -> PasswordPolicy.validate("agua"));
        assertThat(e.unmetRules()).containsExactly("MIN_LENGTH", "UPPERCASE", "DIGIT", "SPECIAL");
    }

    @Test
    void password_policy_does_not_count_spaces_as_special() {
        WeakPasswordException e = catchThrowableOfType(WeakPasswordException.class, () -> PasswordPolicy.validate("Agua 2026"));
        assertThat(e.unmetRules()).containsExactly("SPECIAL");
    }

    // ── User ─────────────────────────────────────────────────────────────

    @Test
    void register_starts_unverified_with_role_user() {
        User user = newUser();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.roles()).containsExactly(Role.USER);
        assertThat(user.createdAt()).isEqualTo(NOW);
    }

    @Test
    void names_are_cleaned_and_validated() {
        User user = User.register("  maría   josé ", "O'Neil-Pérez", new Email("m@mail.com"), null, NOW);
        assertThat(user.firstName()).isEqualTo("maría josé");
        assertThatThrownBy(() -> User.register("J", "Ome", new Email("j@mail.com"), null, NOW))
                .isInstanceOf(InvalidUserDataException.class);
        assertThatThrownBy(() -> User.register("Juan3", "Ome", new Email("j@mail.com"), null, NOW))
                .isInstanceOf(InvalidUserDataException.class);
    }

    @Test
    void login_checks_follow_the_hu003_order() {
        User locked = User.restore(UUID.randomUUID(), "Ana", "Ruiz", new Email("a@mail.com"), null, null, true,
                NOW.plusSeconds(60), null, null, NOW, NOW, EnumSet.of(Role.USER));
        assertThatThrownBy(() -> locked.ensureCanAttemptLogin(NOW)).isInstanceOf(AccountLockedException.class);
        locked.ensureCanAttemptLogin(NOW.plusSeconds(61));   // the lock ended

        User blocked = User.restore(UUID.randomUUID(), "Ana", "Ruiz", new Email("b@mail.com"), null, null, true,
                null, NOW, null, NOW, NOW, EnumSet.of(Role.USER));
        assertThatThrownBy(() -> blocked.ensureCanAttemptLogin(NOW)).isInstanceOf(AccountBlockedException.class);

        User deleted = newUser();
        deleted.delete(NOW);
        assertThatThrownBy(() -> deleted.ensureCanAttemptLogin(NOW)).isInstanceOf(EmailNotFoundException.class);

        assertThatThrownBy(() -> newUser().ensureVerified()).isInstanceOf(AccountNotVerifiedException.class);
    }

    @Test
    void verify_email_only_once() {
        User user = newUser();
        user.verifyEmail(NOW);
        assertThat(user.isEmailVerified()).isTrue();
        assertThatThrownBy(() -> user.verifyEmail(NOW)).isInstanceOf(AlreadyVerifiedException.class);
    }

    @Test
    void delete_anonymizes_and_frees_the_email() {
        User user = User.register("Juan", "Ome", new Email("juan@mail.com"), new PhoneNumber("3001234567"), NOW);
        user.changeAvatar("/api/avatars/x.jpg", NOW);

        user.delete(NOW.plusSeconds(5));

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.email().value()).startsWith("deleted-").endsWith("@deleted.invalid");
        assertThat(user.phone()).isNull();
        assertThat(user.avatarUrl()).isNull();
        assertThat(user.fullName()).isEqualTo("Usuario Eliminado");
    }

    // ── Lockout (HU-003) ─────────────────────────────────────────────────

    @Test
    void lockout_counts_only_recent_failures_after_the_last_success() {
        LockoutPolicy policy = new LockoutPolicy(5, Duration.ofMinutes(15));

        assertThat(policy.countFailuresSince(NOW, null, null)).isEqualTo(NOW.minus(Duration.ofMinutes(15)));
        assertThat(policy.countFailuresSince(NOW, NOW.minusSeconds(60), null)).isEqualTo(NOW.minusSeconds(60));
        // After a lock ends, the failures that caused it are forgotten
        assertThat(policy.countFailuresSince(NOW, null, NOW.minusSeconds(10))).isEqualTo(NOW.minusSeconds(10));
        // A lock that has NOT ended yet does not move the window
        assertThat(policy.countFailuresSince(NOW, null, NOW.plusSeconds(10))).isEqualTo(NOW.minus(Duration.ofMinutes(15)));

        assertThat(policy.remainingAttempts(1)).isEqualTo(4);
        assertThat(policy.shouldLock(4)).isFalse();
        assertThat(policy.shouldLock(5)).isTrue();
    }
}
