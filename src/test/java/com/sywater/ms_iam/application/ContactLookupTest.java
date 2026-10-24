package com.sywater.ms_iam.application;

import com.sywater.ms_iam.application.dto.ContactView;
import com.sywater.ms_iam.application.service.ContactLookupService;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contact lookup for other services (ms-notification mails the critical alerts with it). */
class ContactLookupTest {

    private final TestWorld w = new TestWorld();
    private final ContactLookupService contacts = new ContactLookupService(w.users);

    @Test
    void a_verified_account_has_a_contact() {
        UUID id = w.verifiedUser("juan@mail.com");

        ContactView contact = contacts.findContact(id);

        assertThat(contact.id()).isEqualTo(id);
        assertThat(contact.email()).isEqualTo("juan@mail.com");
        assertThat(contact.fullName()).isEqualTo("Juan Ome");
    }

    @Test
    void the_account_behind_an_email_is_found_whatever_the_case_or_spaces() {
        UUID id = w.verifiedUser("ana@mail.com");

        ContactView contact = contacts.findContactByEmail("  Ana@Mail.COM ");

        assertThat(contact.id()).isEqualTo(id);
        assertThat(contact.email()).isEqualTo("ana@mail.com");
    }

    @Test
    void an_email_nobody_uses_or_that_is_not_an_email_is_not_found() {
        assertThatThrownBy(() -> contacts.findContactByEmail("nadie@mail.com")).isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> contacts.findContactByEmail("not-an-email")).isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> contacts.findContactByEmail(null)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void an_unverified_or_deleted_account_cannot_be_found_by_email() {
        w.registration.register(new com.sywater.ms_iam.application.port.in.RegisterUserUseCase.Command(
                "Juan", "Ome", "nuevo@mail.com", null, TestWorld.PASSWORD));
        UUID id = w.verifiedUser("borrado@mail.com");
        w.deletion.deleteAccount(id, TestWorld.PASSWORD, TestWorld.CTX);

        assertThatThrownBy(() -> contacts.findContactByEmail("nuevo@mail.com")).isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> contacts.findContactByEmail("borrado@mail.com")).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void an_unknown_user_has_no_contact() {
        assertThatThrownBy(() -> contacts.findContact(UUID.randomUUID())).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void an_unverified_account_has_no_contact_yet() {
        w.registration.register(new com.sywater.ms_iam.application.port.in.RegisterUserUseCase.Command(
                "Juan", "Ome", "nuevo@mail.com", null, TestWorld.PASSWORD));
        UUID id = w.users.findByEmail(new com.sywater.ms_iam.domain.model.Email("nuevo@mail.com")).orElseThrow().id();

        assertThatThrownBy(() -> contacts.findContact(id)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void a_deleted_account_has_no_contact_because_its_address_was_erased() {
        UUID id = w.verifiedUser("juan@mail.com");
        w.deletion.deleteAccount(id, TestWorld.PASSWORD, TestWorld.CTX);

        assertThatThrownBy(() -> contacts.findContact(id)).isInstanceOf(UserNotFoundException.class);
    }
}
