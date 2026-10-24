package com.sywater.ms_iam.application;

import com.sywater.ms_iam.application.port.in.InvitationEmailUseCase;
import com.sywater.ms_iam.application.service.InvitationEmailService;
import com.sywater.ms_iam.domain.exception.InvalidEmailException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-042: ms-places asks ms-iam to send the invitation to collaborate in a place. */
class InvitationEmailTest {

    private final Fakes.Mailbox mailbox = new Fakes.Mailbox();
    private final InvitationEmailService service = new InvitationEmailService(mailbox);
    private static final Instant EXPIRES = Instant.parse("2026-10-16T15:00:00Z");

    @Test
    void the_invited_person_gets_the_mail_even_without_an_account() {
        service.send(new InvitationEmailUseCase.Command(
                "Nuevo@Mail.com", "Casa", "Olga", List.of("VIEW_CONSUMPTION", "CONTROL_VALVE"), EXPIRES));

        assertThat(mailbox.sent).hasSize(1);
        assertThat(mailbox.sent.get(0).kind()).isEqualTo("INVITATION");
        assertThat(mailbox.sent.get(0).to()).isEqualTo("nuevo@mail.com");
        assertThat(mailbox.sent.get(0).code()).isEqualTo("Casa");
    }

    @Test
    void an_address_that_is_not_an_email_is_refused_and_nothing_is_sent() {
        assertThatThrownBy(() -> service.send(new InvitationEmailUseCase.Command(
                "no-es-correo", "Casa", "Olga", List.of(), EXPIRES))).isInstanceOf(InvalidEmailException.class);
        assertThat(mailbox.sent).isEmpty();
    }

    @Test
    void a_missing_place_name_or_permission_list_does_not_break_the_mail() {
        service.send(new InvitationEmailUseCase.Command("a@mail.com", " ", null, null, EXPIRES));

        assertThat(mailbox.sent.get(0).code()).isEqualTo("un lugar");
    }
}
