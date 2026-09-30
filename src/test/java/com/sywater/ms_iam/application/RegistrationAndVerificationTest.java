package com.sywater.ms_iam.application;

import com.sywater.ms_iam.application.dto.CodeSent;
import com.sywater.ms_iam.application.port.in.RegisterUserUseCase;
import com.sywater.ms_iam.domain.exception.AccountNotFoundException;
import com.sywater.ms_iam.domain.exception.AlreadyVerifiedException;
import com.sywater.ms_iam.domain.exception.CodeExpiredException;
import com.sywater.ms_iam.domain.exception.CodeRecentlySentException;
import com.sywater.ms_iam.domain.exception.EmailAlreadyRegisteredException;
import com.sywater.ms_iam.domain.exception.InvalidCodeException;
import com.sywater.ms_iam.domain.exception.PhoneAlreadyRegisteredException;
import com.sywater.ms_iam.domain.exception.WeakPasswordException;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RegistrationAndVerificationTest {

    private final TestWorld w = new TestWorld();

    private CodeSent register(String email, String phone) {
        return w.registration.register(new RegisterUserUseCase.Command("Juan", "Ome", email, phone, TestWorld.PASSWORD));
    }

    private User user(String email) {
        return w.users.findByEmail(new Email(email)).orElseThrow();
    }

    @Test
    void register_saves_hash_sends_code_and_masks_email() {
        CodeSent sent = register("Juan@Mail.com", "300 123 4567");

        User user = user("juan@mail.com");
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.phone().value()).isEqualTo("3001234567");
        assertThat(w.credentials.hashes.get(user.id())).isEqualTo("H(" + TestWorld.PASSWORD + ")");   // never plain
        assertThat(w.mailbox.sent).singleElement().satisfies(m -> {
            assertThat(m.kind()).isEqualTo("VERIFY");
            assertThat(m.code()).isEqualTo("111111");
        });
        assertThat(sent.maskedEmail()).isEqualTo("ju**@mail.com");
        assertThat(sent.expiresAt()).isEqualTo(w.now.plus(Duration.ofHours(24)));
        // Only the hash of "userId:code" is stored
        assertThat(w.codes.rows.get(0).hash()).isEqualTo("H(" + user.id() + ":111111)");
    }

    @Test
    void register_rejects_duplicates_and_weak_passwords_without_saving() {
        register("juan@mail.com", "3001234567");

        assertThatThrownBy(() -> register("JUAN@mail.com", null)).isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThatThrownBy(() -> register("otro@mail.com", "300-123-4567")).isInstanceOf(PhoneAlreadyRegisteredException.class);
        assertThatThrownBy(() -> w.registration.register(
                new RegisterUserUseCase.Command("Ana", "Ruiz", "ana@mail.com", null, "debil")))
                .isInstanceOf(WeakPasswordException.class);
        assertThat(w.users.byId).hasSize(1);
    }

    @Test
    void verify_with_the_right_code_activates_the_account() {
        register("juan@mail.com", null);

        w.registration.verify("juan@mail.com", "111111");

        assertThat(user("juan@mail.com").isEmailVerified()).isTrue();
        assertThat(w.activity.actions).contains("REGISTERED", "EMAIL_VERIFIED");
        // Single use
        assertThatThrownBy(() -> w.registration.verify("juan@mail.com", "111111"))
                .isInstanceOf(AlreadyVerifiedException.class);
    }

    @Test
    void wrong_codes_count_down_and_the_fifth_kills_the_code() {
        register("juan@mail.com", null);

        InvalidCodeException first = catchThrowableOfType(InvalidCodeException.class,
                () -> w.registration.verify("juan@mail.com", "000000"));
        assertThat(first.remainingAttempts()).isEqualTo(4);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> w.registration.verify("juan@mail.com", "000000")).isInstanceOf(InvalidCodeException.class);
        }
        // Even the right code no longer works: ask for a new one
        assertThatThrownBy(() -> w.registration.verify("juan@mail.com", "111111")).isInstanceOf(CodeExpiredException.class);
    }

    @Test
    void code_expires_after_24_hours() {
        register("juan@mail.com", null);
        w.advance(Duration.ofHours(24).plusSeconds(1));

        assertThatThrownBy(() -> w.registration.verify("juan@mail.com", "111111")).isInstanceOf(CodeExpiredException.class);
    }

    @Test
    void resend_kills_the_old_code_and_respects_the_cooldown() {
        register("juan@mail.com", null);

        assertThatThrownBy(() -> w.registration.resend("juan@mail.com")).isInstanceOf(CodeRecentlySentException.class);

        w.advance(Duration.ofSeconds(61));
        w.registration.resend("juan@mail.com");
        assertThat(w.mailbox.lastCode()).isEqualTo("222222");

        assertThatThrownBy(() -> w.registration.verify("juan@mail.com", "111111")).isInstanceOf(InvalidCodeException.class);
        w.registration.verify("juan@mail.com", "222222");
        assertThat(user("juan@mail.com").isEmailVerified()).isTrue();
    }

    @Test
    void verify_unknown_email_is_account_not_found() {
        assertThatThrownBy(() -> w.registration.verify("nadie@mail.com", "111111")).isInstanceOf(AccountNotFoundException.class);
    }
}
