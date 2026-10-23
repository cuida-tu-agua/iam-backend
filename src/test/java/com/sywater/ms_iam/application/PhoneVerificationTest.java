package com.sywater.ms_iam.application;

import com.sywater.ms_iam.application.dto.PhoneCodeSent;
import com.sywater.ms_iam.application.dto.UserView;
import com.sywater.ms_iam.application.port.out.PhoneVerificationRepository;
import com.sywater.ms_iam.application.port.out.ShortMessageGateway;
import com.sywater.ms_iam.application.service.ContactLookupService;
import com.sywater.ms_iam.application.service.PhoneVerificationService;
import com.sywater.ms_iam.domain.exception.AccountBlockedException;
import com.sywater.ms_iam.domain.exception.CodeExpiredException;
import com.sywater.ms_iam.domain.exception.CodeRecentlySentException;
import com.sywater.ms_iam.domain.exception.ExternalServiceUnavailableException;
import com.sywater.ms_iam.domain.exception.InvalidCodeException;
import com.sywater.ms_iam.domain.exception.InvalidUserDataException;
import com.sywater.ms_iam.domain.exception.PhoneAlreadyRegisteredException;
import com.sywater.ms_iam.domain.exception.PhoneNotReachableException;
import com.sywater.ms_iam.domain.model.PhoneChannel;
import com.sywater.ms_iam.domain.model.PhoneNumber;
import com.sywater.ms_iam.domain.model.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-028: verify the phone by SMS or WhatsApp; the mode chosen here is the one the alerts will use. */
class PhoneVerificationTest {

    /** In-memory security.action_codes rows with action PHONE_VERIFY. */
    static final class PhoneCodes implements PhoneVerificationRepository {
        record Row(long id, UUID userId, String hash, Instant expiresAt, Instant createdAt, int failed, Instant usedAt,
                   PhoneNumber phone, PhoneChannel channel) {}

        final List<Row> rows = new ArrayList<>();

        @Override public void issue(UUID userId, String codeHash, PhoneNumber phone, PhoneChannel channel, Instant expiresAt, Instant now) {
            rows.replaceAll(r -> r.userId().equals(userId) && r.usedAt() == null && r.expiresAt().isAfter(now)
                    ? new Row(r.id(), r.userId(), r.hash(), now, r.createdAt(), r.failed(), r.usedAt(), r.phone(), r.channel()) : r);
            rows.add(new Row(rows.size() + 1, userId, codeHash, expiresAt, now, 0, null, phone, channel));
        }
        @Override public Optional<PendingCode> findActive(UUID userId, Instant now) {
            return rows.stream().filter(r -> r.userId().equals(userId) && r.usedAt() == null && r.expiresAt().isAfter(now))
                    .reduce((a, b) -> b)
                    .map(r -> new PendingCode(r.id(), r.hash(), r.expiresAt(), r.failed(), r.phone(), r.channel()));
        }
        @Override public Optional<Instant> lastIssuedAt(UUID userId) {
            return rows.stream().filter(r -> r.userId().equals(userId)).reduce((a, b) -> b).map(Row::createdAt);
        }
        @Override public int registerFailedAttempt(long codeId) {
            Row r = rows.get((int) codeId - 1);
            rows.set((int) codeId - 1, new Row(r.id(), r.userId(), r.hash(), r.expiresAt(), r.createdAt(), r.failed() + 1, r.usedAt(), r.phone(), r.channel()));
            return r.failed() + 1;
        }
        @Override public boolean consume(long codeId, Instant now) {
            Row r = rows.get((int) codeId - 1);
            if (r.usedAt() != null) return false;
            rows.set((int) codeId - 1, new Row(r.id(), r.userId(), r.hash(), r.expiresAt(), r.createdAt(), r.failed(), now, r.phone(), r.channel()));
            return true;
        }
    }

    /** ms-notification: remembers what it was asked to send. */
    static final class Messages implements ShortMessageGateway {
        record Sent(PhoneNumber to, PhoneChannel channel, String code, Duration validFor) {}

        final List<Sent> sent = new ArrayList<>();
        boolean refuse;
        boolean down;

        @Override public void sendVerificationCode(PhoneNumber to, PhoneChannel channel, String code, Duration validFor) {
            if (down) throw new ExternalServiceUnavailableException("notification");
            if (refuse) throw new PhoneNotReachableException(channel.name());
            sent.add(new Sent(to, channel, code, validFor));
        }

        String lastCode() { return sent.get(sent.size() - 1).code(); }
    }

    private final TestWorld w = new TestWorld();
    private final PhoneCodes codes = new PhoneCodes();
    private final Messages messages = new Messages();
    private final PhoneVerificationService phones = new PhoneVerificationService(w.users, codes, w.generator, w.hasher,
            messages, w.activity, w.settings, Duration.ofMinutes(5), w.clock);

    @Test
    void the_code_goes_by_the_chosen_mode_and_confirming_it_verifies_the_phone_with_that_mode() {
        UUID id = w.verifiedUser("juan@mail.com");

        PhoneCodeSent sent = phones.requestCode(id, "CO", "300 111 2233", "whatsapp", TestWorld.CTX);

        assertThat(sent.maskedPhone()).isEqualTo("+57300***233");
        assertThat(sent.channel()).isEqualTo("WHATSAPP");
        assertThat(sent.expiresAt()).isEqualTo(w.now.plus(Duration.ofMinutes(5)));
        Messages.Sent message = messages.sent.get(0);
        assertThat(message.to().value()).isEqualTo("+573001112233");
        assertThat(message.channel()).isEqualTo(PhoneChannel.WHATSAPP);
        assertThat(w.users.byId.get(id).isPhoneVerified()).isFalse();   // nothing changes until the code is confirmed

        UserView view = phones.confirm(id, messages.lastCode(), TestWorld.CTX);

        assertThat(view.phone()).isEqualTo("+573001112233");
        assertThat(view.phoneVerified()).isTrue();
        assertThat(view.phoneChannel()).isEqualTo("WHATSAPP");
        assertThat(w.activity.actions).contains("PHONE_CODE_REQUESTED", "PHONE_VERIFIED");
        assertThatThrownBy(() -> phones.confirm(id, messages.lastCode(), TestWorld.CTX))
                .isInstanceOf(CodeExpiredException.class);   // single use
    }

    @Test
    void an_ecuadorian_number_by_sms() {
        UUID id = w.verifiedUser("ana@mail.com");

        phones.requestCode(id, "EC", "0991112233", "SMS", TestWorld.CTX);
        UserView view = phones.confirm(id, messages.lastCode(), TestWorld.CTX);

        assertThat(view.phone()).isEqualTo("+593991112233");
        assertThat(view.phoneChannel()).isEqualTo("SMS");
    }

    @Test
    void a_number_that_does_not_work_for_the_mode_is_refused_and_no_code_is_left() {
        UUID id = w.verifiedUser("juan@mail.com");
        messages.refuse = true;

        assertThatThrownBy(() -> phones.requestCode(id, "CO", "3001112233", "WHATSAPP", TestWorld.CTX))
                .isInstanceOf(PhoneNotReachableException.class);
        assertThat(codes.rows).isEmpty();
    }

    @Test
    void the_sms_service_down_is_503_and_no_code_is_left() {
        UUID id = w.verifiedUser("juan@mail.com");
        messages.down = true;

        assertThatThrownBy(() -> phones.requestCode(id, "CO", "3001112233", "SMS", TestWorld.CTX))
                .isInstanceOf(ExternalServiceUnavailableException.class);
        assertThat(codes.rows).isEmpty();
    }

    @Test
    void bad_country_number_or_mode_is_400_before_sending_anything() {
        UUID id = w.verifiedUser("juan@mail.com");

        assertThatThrownBy(() -> phones.requestCode(id, "PE", "3001112233", "SMS", TestWorld.CTX)).isInstanceOf(InvalidUserDataException.class);
        assertThatThrownBy(() -> phones.requestCode(id, "CO", "6011112233", "SMS", TestWorld.CTX)).isInstanceOf(InvalidUserDataException.class);
        assertThatThrownBy(() -> phones.requestCode(id, "CO", "3001112233", "TELEGRAM", TestWorld.CTX)).isInstanceOf(InvalidUserDataException.class);
        assertThat(messages.sent).isEmpty();
    }

    @Test
    void a_number_of_another_account_cannot_be_verified() {
        UUID owner = w.verifiedUser("ana@mail.com");
        phones.requestCode(owner, "CO", "3001112233", "SMS", TestWorld.CTX);
        phones.confirm(owner, messages.lastCode(), TestWorld.CTX);
        UUID other = w.verifiedUser("juan@mail.com");

        assertThatThrownBy(() -> phones.requestCode(other, "CO", "3001112233", "SMS", TestWorld.CTX))
                .isInstanceOf(PhoneAlreadyRegisteredException.class);
    }

    @Test
    void a_new_code_waits_60_seconds_and_replaces_the_previous_one() {
        UUID id = w.verifiedUser("juan@mail.com");
        phones.requestCode(id, "CO", "3001112233", "SMS", TestWorld.CTX);
        String first = messages.lastCode();

        assertThatThrownBy(() -> phones.requestCode(id, "CO", "3001112233", "WHATSAPP", TestWorld.CTX))
                .isInstanceOf(CodeRecentlySentException.class);

        w.advance(Duration.ofSeconds(61));
        phones.requestCode(id, "CO", "3001112233", "WHATSAPP", TestWorld.CTX);
        String second = messages.lastCode();

        if (!first.equals(second)) {
            assertThatThrownBy(() -> phones.confirm(id, first, TestWorld.CTX)).isInstanceOf(InvalidCodeException.class);
        }
        assertThat(phones.confirm(id, second, TestWorld.CTX).phoneChannel()).isEqualTo("WHATSAPP");   // the LAST mode chosen
    }

    @Test
    void wrong_codes_count_down_and_after_five_the_code_is_gone() {
        UUID id = w.verifiedUser("juan@mail.com");
        phones.requestCode(id, "CO", "3001112233", "SMS", TestWorld.CTX);
        String wrong = messages.lastCode().equals("000000") ? "111111" : "000000";

        for (int left = 4; left >= 0; left--) {
            int expected = left;
            assertThatThrownBy(() -> phones.confirm(id, wrong, TestWorld.CTX))
                    .isInstanceOfSatisfying(InvalidCodeException.class, e -> assertThat(e.remainingAttempts()).isEqualTo(expected));
        }
        assertThatThrownBy(() -> phones.confirm(id, messages.lastCode(), TestWorld.CTX)).isInstanceOf(CodeExpiredException.class);
        assertThat(w.users.byId.get(id).isPhoneVerified()).isFalse();
    }

    @Test
    void the_code_expires_in_5_minutes() {
        UUID id = w.verifiedUser("juan@mail.com");
        phones.requestCode(id, "CO", "3001112233", "SMS", TestWorld.CTX);

        w.advance(Duration.ofMinutes(5).plusSeconds(1));

        assertThatThrownBy(() -> phones.confirm(id, messages.lastCode(), TestWorld.CTX)).isInstanceOf(CodeExpiredException.class);
    }

    @Test
    void a_blocked_account_cannot_verify_a_phone() {
        UUID id = w.verifiedUser("juan@mail.com");
        User user = w.users.byId.get(id);
        user.block(UUID.randomUUID(), w.now);

        assertThatThrownBy(() -> phones.requestCode(id, "CO", "3001112233", "SMS", TestWorld.CTX))
                .isInstanceOf(AccountBlockedException.class);
    }

    @Test
    void ms_notification_gets_the_verified_phone_and_its_mode_in_the_contact() {
        UUID id = w.verifiedUser("juan@mail.com");
        ContactLookupService contacts = new ContactLookupService(w.users);
        assertThat(contacts.findContact(id).phoneVerified()).isFalse();
        assertThat(contacts.findContact(id).phoneChannel()).isNull();

        phones.requestCode(id, "EC", "991112233", "WHATSAPP", TestWorld.CTX);
        phones.confirm(id, messages.lastCode(), TestWorld.CTX);

        var contact = contacts.findContact(id);
        assertThat(contact.phone()).isEqualTo("+593991112233");
        assertThat(contact.phoneVerified()).isTrue();
        assertThat(contact.phoneChannel()).isEqualTo("WHATSAPP");
    }
}
