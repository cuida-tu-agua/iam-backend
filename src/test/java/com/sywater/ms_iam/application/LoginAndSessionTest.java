package com.sywater.ms_iam.application;

import com.sywater.ms_iam.application.dto.AuthResult;
import com.sywater.ms_iam.application.port.in.RegisterUserUseCase;
import com.sywater.ms_iam.domain.exception.AccountLockedException;
import com.sywater.ms_iam.domain.exception.AccountNotVerifiedException;
import com.sywater.ms_iam.domain.exception.EmailNotFoundException;
import com.sywater.ms_iam.domain.exception.InvalidRefreshTokenException;
import com.sywater.ms_iam.domain.exception.WrongPasswordException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class LoginAndSessionTest {

    private final TestWorld w = new TestWorld();

    @Test
    void login_returns_both_tokens_and_logs_the_attempt() {
        UUID id = w.verifiedUser("juan@mail.com");

        AuthResult result = w.auth.login("JUAN@mail.com ", TestWorld.PASSWORD, TestWorld.CTX);

        assertThat(result.accessToken()).isEqualTo("access-1");
        assertThat(result.accessExpiresAt()).isEqualTo(w.now.plus(Duration.ofHours(1)));
        assertThat(result.refreshToken()).isEqualTo("refresh-1");
        assertThat(result.refreshExpiresAt()).isEqualTo(w.now.plus(Duration.ofDays(7)));
        assertThat(result.user().id()).isEqualTo(id);
        assertThat(w.refreshTokens.rows.get(0).hash()).isEqualTo("H(refresh-1)");   // only the hash is stored
        assertThat(w.attempts.rows).singleElement().satisfies(a -> assertThat(a.success()).isTrue());
    }

    @Test
    void differentiated_errors_of_hu003() {
        w.registration.register(new RegisterUserUseCase.Command("Ana", "Ruiz", "ana@mail.com", null, TestWorld.PASSWORD));

        assertThatThrownBy(() -> w.auth.login("nadie@mail.com", TestWorld.PASSWORD, TestWorld.CTX))
                .isInstanceOf(EmailNotFoundException.class);
        // Wrong password is reported BEFORE "not verified": the status is not revealed without the password
        assertThatThrownBy(() -> w.auth.login("ana@mail.com", "Otra2026!", TestWorld.CTX))
                .isInstanceOf(WrongPasswordException.class);
        assertThatThrownBy(() -> w.auth.login("ana@mail.com", TestWorld.PASSWORD, TestWorld.CTX))
                .isInstanceOf(AccountNotVerifiedException.class);

        assertThat(w.attempts.rows).extracting(a -> a.reason())
                .containsExactly("EMAIL_NOT_FOUND", "WRONG_PASSWORD", "ACCOUNT_NOT_VERIFIED");
    }

    @Test
    void five_wrong_passwords_lock_the_account_for_15_minutes() {
        w.verifiedUser("juan@mail.com");

        for (int expected = 4; expected >= 1; expected--) {
            WrongPasswordException e = catchThrowableOfType(WrongPasswordException.class,
                    () -> w.auth.login("juan@mail.com", "Mala2026!", TestWorld.CTX));
            assertThat(e.remainingAttempts()).isEqualTo(expected);
        }
        AccountLockedException locked = catchThrowableOfType(AccountLockedException.class,
                () -> w.auth.login("juan@mail.com", "Mala2026!", TestWorld.CTX));
        assertThat(locked.lockedUntil()).isEqualTo(w.now.plus(Duration.ofMinutes(15)));

        // Even the right password is refused during the lock
        assertThatThrownBy(() -> w.auth.login("juan@mail.com", TestWorld.PASSWORD, TestWorld.CTX))
                .isInstanceOf(AccountLockedException.class);

        // After 15 minutes it works, and the old failures are forgotten
        w.advance(Duration.ofMinutes(15).plusSeconds(1));
        WrongPasswordException fresh = catchThrowableOfType(WrongPasswordException.class,
                () -> w.auth.login("juan@mail.com", "Mala2026!", TestWorld.CTX));
        assertThat(fresh.remainingAttempts()).isEqualTo(4);
        assertThat(w.auth.login("juan@mail.com", TestWorld.PASSWORD, TestWorld.CTX).accessToken()).isNotBlank();
    }

    @Test
    void a_successful_login_resets_the_failure_count() {
        w.verifiedUser("juan@mail.com");
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> w.auth.login("juan@mail.com", "Mala2026!", TestWorld.CTX)).isInstanceOf(WrongPasswordException.class);
        }
        w.advance(Duration.ofSeconds(1));
        w.auth.login("juan@mail.com", TestWorld.PASSWORD, TestWorld.CTX);
        w.advance(Duration.ofSeconds(1));

        WrongPasswordException e = catchThrowableOfType(WrongPasswordException.class,
                () -> w.auth.login("juan@mail.com", "Mala2026!", TestWorld.CTX));
        assertThat(e.remainingAttempts()).isEqualTo(4);
    }

    @Test
    void refresh_rotates_the_token() {
        w.verifiedUser("juan@mail.com");
        AuthResult first = w.auth.login("juan@mail.com", TestWorld.PASSWORD, TestWorld.CTX);

        AuthResult second = w.auth.refresh(first.refreshToken(), TestWorld.CTX);

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.accessToken()).isEqualTo("access-2");
        assertThat(w.refreshTokens.rows.get(0).revokedAt()).isNotNull();   // the old one is dead
    }

    @Test
    void reusing_an_old_refresh_token_closes_every_session() {
        UUID id = w.verifiedUser("juan@mail.com");
        AuthResult first = w.auth.login("juan@mail.com", TestWorld.PASSWORD, TestWorld.CTX);
        w.auth.refresh(first.refreshToken(), TestWorld.CTX);

        assertThatThrownBy(() -> w.auth.refresh(first.refreshToken(), TestWorld.CTX))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(w.refreshTokens.active(id)).isZero();
        assertThat(w.revocations.revokedBefore).containsKey(id);
        assertThat(w.activity.actions).contains("REFRESH_TOKEN_REUSED");
    }

    @Test
    void expired_or_unknown_refresh_tokens_are_rejected() {
        w.verifiedUser("juan@mail.com");
        AuthResult result = w.auth.login("juan@mail.com", TestWorld.PASSWORD, TestWorld.CTX);

        assertThatThrownBy(() -> w.auth.refresh("inventado", TestWorld.CTX)).isInstanceOf(InvalidRefreshTokenException.class);
        w.advance(Duration.ofDays(7).plusSeconds(1));
        assertThatThrownBy(() -> w.auth.refresh(result.refreshToken(), TestWorld.CTX)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logout_revokes_the_access_token_and_the_refresh_token() {
        UUID id = w.verifiedUser("juan@mail.com");
        AuthResult result = w.auth.login("juan@mail.com", TestWorld.PASSWORD, TestWorld.CTX);

        w.auth.logout(id, "jti-1", result.accessExpiresAt(), result.refreshToken(), TestWorld.CTX);

        assertThat(w.revocations.revokedTokenIds).containsExactly("jti-1");
        assertThat(w.refreshTokens.active(id)).isZero();
        assertThatThrownBy(() -> w.auth.refresh(result.refreshToken(), TestWorld.CTX)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logout_cannot_revoke_someone_elses_refresh_token() {
        UUID juan = w.verifiedUser("juan@mail.com");
        UUID ana = w.verifiedUser("ana@mail.com");
        AuthResult anas = w.auth.login("ana@mail.com", TestWorld.PASSWORD, TestWorld.CTX);

        w.auth.logout(juan, "jti-x", w.now.plusSeconds(60), anas.refreshToken(), TestWorld.CTX);

        assertThat(w.refreshTokens.active(ana)).isEqualTo(1);
    }
}
