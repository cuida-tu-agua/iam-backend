package com.sywater.ms_iam.application;

import com.sywater.ms_iam.application.dto.CodeSent;
import com.sywater.ms_iam.domain.exception.CodeExpiredException;
import com.sywater.ms_iam.domain.exception.CodeRecentlySentException;
import com.sywater.ms_iam.domain.exception.InvalidCodeException;
import com.sywater.ms_iam.domain.exception.UnknownActionException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** HU-020: code that confirms closing the valve */
class ActionCodeTest {

    private final TestWorld w = new TestWorld();

    @Test
    void the_code_goes_to_the_email_and_works_once() {
        UUID id = w.verifiedUser("juan@mail.com");

        CodeSent sent = w.actionCodes.requestCode(id, "VALVE_CLOSE", TestWorld.CTX);
        assertThat(sent.maskedEmail()).isEqualTo("ju**@mail.com");
        assertThat(sent.expiresAt()).isEqualTo(w.now.plus(Duration.ofMinutes(5)));
        assertThat(w.mailbox.sent.get(w.mailbox.sent.size() - 1).kind()).isEqualTo("ACTION");

        String code = w.mailbox.lastCode();
        assertThatCode(() -> w.actionCodes.verifyCode(id, "valve_close", code, TestWorld.CTX)).doesNotThrowAnyException();
        assertThatThrownBy(() -> w.actionCodes.verifyCode(id, "VALVE_CLOSE", code, TestWorld.CTX))
                .isInstanceOf(CodeExpiredException.class);                                   // single use
        assertThat(w.activity.actions).contains("ACTION_CODE_REQUESTED", "ACTION_CODE_VERIFIED");
    }

    @Test
    void expires_in_5_minutes() {
        UUID id = w.verifiedUser("juan@mail.com");
        w.actionCodes.requestCode(id, "VALVE_CLOSE", TestWorld.CTX);
        String code = w.mailbox.lastCode();

        w.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertThatThrownBy(() -> w.actionCodes.verifyCode(id, "VALVE_CLOSE", code, TestWorld.CTX))
                .isInstanceOf(CodeExpiredException.class);
    }

    @Test
    void wrong_codes_count_down_and_the_fifth_kills_the_code() {
        UUID id = w.verifiedUser("juan@mail.com");
        w.actionCodes.requestCode(id, "VALVE_CLOSE", TestWorld.CTX);
        String right = w.mailbox.lastCode();

        InvalidCodeException first = catchThrowableOfType(InvalidCodeException.class,
                () -> w.actionCodes.verifyCode(id, "VALVE_CLOSE", "000000", TestWorld.CTX));
        assertThat(first.remainingAttempts()).isEqualTo(4);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> w.actionCodes.verifyCode(id, "VALVE_CLOSE", "000000", TestWorld.CTX))
                    .isInstanceOf(InvalidCodeException.class);
        }
        assertThatThrownBy(() -> w.actionCodes.verifyCode(id, "VALVE_CLOSE", right, TestWorld.CTX))
                .isInstanceOf(CodeExpiredException.class);
    }

    @Test
    void one_email_per_minute_and_a_new_code_kills_the_old_one() {
        UUID id = w.verifiedUser("juan@mail.com");
        w.actionCodes.requestCode(id, "VALVE_CLOSE", TestWorld.CTX);
        String old = w.mailbox.lastCode();

        assertThatThrownBy(() -> w.actionCodes.requestCode(id, "VALVE_CLOSE", TestWorld.CTX))
                .isInstanceOf(CodeRecentlySentException.class);

        w.advance(Duration.ofSeconds(61));
        w.actionCodes.requestCode(id, "VALVE_CLOSE", TestWorld.CTX);
        assertThatThrownBy(() -> w.actionCodes.verifyCode(id, "VALVE_CLOSE", old, TestWorld.CTX))
                .isInstanceOf(InvalidCodeException.class);                // only the newest code works
        assertThatCode(() -> w.actionCodes.verifyCode(id, "VALVE_CLOSE", w.mailbox.lastCode(), TestWorld.CTX))
                .doesNotThrowAnyException();
    }

    @Test
    void a_password_reset_code_does_not_close_the_valve() {
        UUID id = w.verifiedUser("juan@mail.com");
        w.recovery.requestReset("juan@mail.com");
        String resetCode = w.mailbox.lastCode();

        assertThatThrownBy(() -> w.actionCodes.verifyCode(id, "VALVE_CLOSE", resetCode, TestWorld.CTX))
                .isInstanceOf(CodeExpiredException.class);   // there is no VALVE_CLOSE code at all
    }

    @Test
    void unknown_actions_are_rejected() {
        UUID id = w.verifiedUser("juan@mail.com");
        assertThatThrownBy(() -> w.actionCodes.requestCode(id, "DELETE_EVERYTHING", TestWorld.CTX))
                .isInstanceOf(UnknownActionException.class);
    }
}
