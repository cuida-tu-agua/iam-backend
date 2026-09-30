package com.sywater.ms_iam.application;

import com.sywater.ms_iam.application.dto.AuthResult;
import com.sywater.ms_iam.application.dto.CodeSent;
import com.sywater.ms_iam.application.dto.UserView;
import com.sywater.ms_iam.application.port.in.ProfileUseCase;
import com.sywater.ms_iam.application.port.in.RegisterUserUseCase;
import com.sywater.ms_iam.domain.exception.AccountNotFoundException;
import com.sywater.ms_iam.domain.exception.CodeExpiredException;
import com.sywater.ms_iam.domain.exception.CurrentPasswordIncorrectException;
import com.sywater.ms_iam.domain.exception.EmailNotFoundException;
import com.sywater.ms_iam.domain.exception.InvalidAvatarException;
import com.sywater.ms_iam.domain.exception.InvalidRefreshTokenException;
import com.sywater.ms_iam.domain.exception.PhoneAlreadyRegisteredException;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import com.sywater.ms_iam.domain.exception.WeakPasswordException;
import com.sywater.ms_iam.domain.exception.WrongPasswordException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RecoveryProfileAndDeletionTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    private final TestWorld w = new TestWorld();

    // ── HU-005 ───────────────────────────────────────────────────────────

    @Test
    void reset_by_phone_sends_the_code_to_the_email_and_closes_all_sessions() {
        w.registration.register(new RegisterUserUseCase.Command("Juan", "Ome", "juan@mail.com", "3001234567", TestWorld.PASSWORD));
        w.registration.verify("juan@mail.com", w.mailbox.lastCode());
        UUID id = w.users.byId.keySet().iterator().next();
        AuthResult session = w.auth.login("juan@mail.com", TestWorld.PASSWORD, TestWorld.CTX);

        CodeSent sent = w.recovery.requestReset("300 123 4567");
        assertThat(sent.maskedEmail()).isEqualTo("ju**@mail.com");
        assertThat(sent.expiresAt()).isEqualTo(w.now.plus(Duration.ofMinutes(15)));

        w.recovery.resetPassword("300-123-4567", w.mailbox.lastCode(), "Nueva2026#", TestWorld.CTX);

        assertThat(w.refreshTokens.active(id)).isZero();                        // "se cierran todas las sesiones"
        assertThat(w.revocations.revokedBefore.get(id)).isEqualTo(w.now);       // ...also the access tokens
        assertThatThrownBy(() -> w.auth.refresh(session.refreshToken(), TestWorld.CTX)).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> w.auth.login("juan@mail.com", TestWorld.PASSWORD, TestWorld.CTX)).isInstanceOf(WrongPasswordException.class);
        assertThat(w.auth.login("juan@mail.com", "Nueva2026#", TestWorld.CTX).accessToken()).isNotBlank();
        assertThat(w.mailbox.sent).extracting(m -> m.kind()).contains("RESET", "CHANGED");
    }

    @Test
    void reset_code_is_single_use_and_expires_in_15_minutes() {
        w.verifiedUser("juan@mail.com");
        w.recovery.requestReset("juan@mail.com");
        String code = w.mailbox.lastCode();

        w.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertThatThrownBy(() -> w.recovery.resetPassword("juan@mail.com", code, "Nueva2026#", TestWorld.CTX))
                .isInstanceOf(CodeExpiredException.class);

        w.recovery.requestReset("juan@mail.com");
        String fresh = w.mailbox.lastCode();
        w.recovery.resetPassword("juan@mail.com", fresh, "Nueva2026#", TestWorld.CTX);
        assertThatThrownBy(() -> w.recovery.resetPassword("juan@mail.com", fresh, "Otra2026#", TestWorld.CTX))
                .isInstanceOf(CodeExpiredException.class);
    }

    @Test
    void a_weak_new_password_does_not_burn_the_code() {
        w.verifiedUser("juan@mail.com");
        w.recovery.requestReset("juan@mail.com");
        String code = w.mailbox.lastCode();

        assertThatThrownBy(() -> w.recovery.resetPassword("juan@mail.com", code, "debil", TestWorld.CTX))
                .isInstanceOf(WeakPasswordException.class);
        w.recovery.resetPassword("juan@mail.com", code, "Nueva2026#", TestWorld.CTX);   // still valid
    }

    @Test
    void after_a_reset_the_old_failures_do_not_lock_the_account_again() {
        w.verifiedUser("juan@mail.com");
        for (int i = 0; i < 5; i++) {   // the 5th wrong password locks the account
            try {
                w.auth.login("juan@mail.com", "Mala2026#", TestWorld.CTX);
            } catch (RuntimeException expected) {
                // WrongPasswordException x4, then AccountLockedException
            }
        }
        w.recovery.requestReset("juan@mail.com");
        w.recovery.resetPassword("juan@mail.com", w.mailbox.lastCode(), "Nueva2026#", TestWorld.CTX);

        // One typo a moment after the reset is a normal "wrong password" (4 left), not a new lock
        w.advance(Duration.ofSeconds(5));
        WrongPasswordException typo = catchThrowableOfType(WrongPasswordException.class,
                () -> w.auth.login("juan@mail.com", "Nuevo2026#", TestWorld.CTX));
        assertThat(typo.remainingAttempts()).isEqualTo(4);
        assertThat(w.auth.login("juan@mail.com", "Nueva2026#", TestWorld.CTX).accessToken()).isNotBlank();
    }

    @Test
    void reset_of_an_unknown_account_says_so() {
        assertThatThrownBy(() -> w.recovery.requestReset("nadie@mail.com")).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> w.recovery.requestReset("12")).isInstanceOf(AccountNotFoundException.class);
    }

    // ── HU-007 ───────────────────────────────────────────────────────────

    @Test
    void update_profile_changes_names_and_phone_but_not_email() {
        UUID id = w.verifiedUser("juan@mail.com");

        UserView view = w.profile.updateProfile(id, new ProfileUseCase.UpdateCommand("Juan Esteban", "Ome Esquivel", "3009998877"), TestWorld.CTX);

        assertThat(view.firstName()).isEqualTo("Juan Esteban");
        assertThat(view.phone()).isEqualTo("3009998877");
        assertThat(view.email()).isEqualTo("juan@mail.com");
    }

    @Test
    void phone_of_another_user_is_rejected() {
        w.registration.register(new RegisterUserUseCase.Command("Ana", "Ruiz", "ana@mail.com", "3001112233", TestWorld.PASSWORD));
        UUID juan = w.verifiedUser("juan@mail.com");

        assertThatThrownBy(() -> w.profile.updateProfile(juan, new ProfileUseCase.UpdateCommand("Juan", "Ome", "300 111 2233"), TestWorld.CTX))
                .isInstanceOf(PhoneAlreadyRegisteredException.class);
    }

    @Test
    void change_password_requires_the_current_one() {
        UUID id = w.verifiedUser("juan@mail.com");

        assertThatThrownBy(() -> w.profile.changePassword(id, "Mala2026!", "Nueva2026#", TestWorld.CTX))
                .isInstanceOf(CurrentPasswordIncorrectException.class);
        w.profile.changePassword(id, TestWorld.PASSWORD, "Nueva2026#", TestWorld.CTX);

        assertThat(w.auth.login("juan@mail.com", "Nueva2026#", TestWorld.CTX).accessToken()).isNotBlank();
    }

    @Test
    void avatar_accepts_real_images_and_replaces_the_old_file() {
        UUID id = w.verifiedUser("juan@mail.com");

        UserView first = w.profile.changeAvatar(id, PNG, "image/png", TestWorld.CTX);
        UserView second = w.profile.changeAvatar(id, JPEG, "image/jpeg", TestWorld.CTX);

        assertThat(first.avatarUrl()).isEqualTo("/api/avatars/1.png");
        assertThat(second.avatarUrl()).isEqualTo("/api/avatars/2.jpg");
        assertThat(w.avatars.deleted).containsExactly("/api/avatars/1.png");
    }

    @Test
    void avatar_rejects_fake_images_and_big_files() {
        UUID id = w.verifiedUser("juan@mail.com");

        assertThatThrownBy(() -> w.profile.changeAvatar(id, "MZ-not-an-image".getBytes(), "image/png", TestWorld.CTX))
                .isInstanceOf(InvalidAvatarException.class);
        assertThatThrownBy(() -> w.profile.changeAvatar(id, PNG, "image/jpeg", TestWorld.CTX))
                .isInstanceOf(InvalidAvatarException.class);
        assertThatThrownBy(() -> w.profile.changeAvatar(id, new byte[3 * 1024 * 1024], "image/png", TestWorld.CTX))
                .isInstanceOf(InvalidAvatarException.class);
    }

    // ── HU-008 ───────────────────────────────────────────────────────────

    @Test
    void delete_account_needs_the_password_and_closes_everything() {
        UUID id = w.verifiedUser("juan@mail.com");
        w.auth.login("juan@mail.com", TestWorld.PASSWORD, TestWorld.CTX);

        assertThatThrownBy(() -> w.deletion.deleteAccount(id, "Mala2026!", TestWorld.CTX))
                .isInstanceOf(CurrentPasswordIncorrectException.class);

        w.deletion.deleteAccount(id, TestWorld.PASSWORD, TestWorld.CTX);

        assertThat(w.refreshTokens.active(id)).isZero();
        assertThat(w.revocations.revokedBefore).containsKey(id);
        assertThat(w.devices.cleaned).containsExactly(id);
        assertThatThrownBy(() -> w.profile.getProfile(id)).isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> w.auth.login("juan@mail.com", TestWorld.PASSWORD, TestWorld.CTX))
                .isInstanceOf(EmailNotFoundException.class);

        // The e-mail is free again
        w.registration.register(new RegisterUserUseCase.Command("Juan", "Ome", "juan@mail.com", null, TestWorld.PASSWORD));
    }
}
