package com.sywater.ms_iam.domain;

import com.sywater.ms_iam.domain.exception.InvalidUserDataException;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.PhoneChannel;
import com.sywater.ms_iam.domain.model.PhoneCountry;
import com.sywater.ms_iam.domain.model.PhoneNumber;
import com.sywater.ms_iam.domain.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-028: countries, modes and the verified phone of a user. */
class PhoneVerificationDomainTest {

    private static final Instant NOW = Instant.parse("2026-10-22T15:00:00Z");

    @ParameterizedTest
    @CsvSource({
            "CO, 3001112233,      +573001112233",
            "co, 300 111 2233,    +573001112233",
            "CO, 300-111-2233,    +573001112233",
            "CO, +573001112233,   +573001112233",
            "CO, 573001112233,    +573001112233",
            "EC, 991112233,       +593991112233",
            "EC, 0991112233,      +593991112233",
            "ec, 099 111 2233,    +593991112233",
            "EC, +593991112233,   +593991112233",
    })
    void the_national_number_becomes_E164_for_its_country(String country, String national, String e164) {
        assertThat(PhoneCountry.parse(country).mobile(national).value()).isEqualTo(e164);
    }

    @ParameterizedTest
    @CsvSource({
            "CO, 6011112233",    // a landline in Bogotá: SMS / WhatsApp need a mobile
            "CO, 300111223",     // 9 digits
            "CO, 30011122334",   // 11 digits
            "EC, 22111223",      // landline
            "EC, 99111223",      // 8 digits
            "CO, abc",
    })
    void anything_but_a_mobile_of_that_country_is_refused(String country, String national) {
        assertThatThrownBy(() -> PhoneCountry.parse(country).mobile(national)).isInstanceOf(InvalidUserDataException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "US", "COL", "PE"})
    void only_colombia_and_ecuador_for_now(String country) {
        assertThatThrownBy(() -> PhoneCountry.parse(country)).isInstanceOf(InvalidUserDataException.class);
    }

    @Test
    void the_mode_is_sms_or_whatsapp_in_any_case() {
        assertThat(PhoneChannel.parse(" whatsapp ")).isEqualTo(PhoneChannel.WHATSAPP);
        assertThat(PhoneChannel.parse("SMS")).isEqualTo(PhoneChannel.SMS);
        assertThatThrownBy(() -> PhoneChannel.parse("EMAIL")).isInstanceOf(InvalidUserDataException.class);
        assertThatThrownBy(() -> PhoneChannel.parse(null)).isInstanceOf(InvalidUserDataException.class);
    }

    @Test
    void a_phone_is_masked() {
        assertThat(new PhoneNumber("+573001112233").masked()).isEqualTo("+57300***233");
    }

    @Test
    void verifying_keeps_the_number_and_the_mode_and_a_new_number_must_be_verified_again() {
        User user = User.register("Juan", "Ome", new Email("juan@mail.com"), PhoneNumber.ofNullable("3001112233"), NOW);
        assertThat(user.isPhoneVerified()).isFalse();
        assertThat(user.phoneChannel()).isNull();

        user.verifyPhone(new PhoneNumber("+573001112233"), PhoneChannel.WHATSAPP, NOW);
        assertThat(user.phone().value()).isEqualTo("+573001112233");
        assertThat(user.isPhoneVerified()).isTrue();
        assertThat(user.phoneChannel()).isEqualTo(PhoneChannel.WHATSAPP);

        user.updateProfile("Juan", "Ome", new PhoneNumber("+573001112233"), NOW);   // same number: still verified
        assertThat(user.isPhoneVerified()).isTrue();

        user.updateProfile("Juan", "Ome", new PhoneNumber("+573009998877"), NOW);   // another number: not proved yet
        assertThat(user.isPhoneVerified()).isFalse();
        assertThat(user.phoneChannel()).isNull();
    }

    @Test
    void deleting_the_account_forgets_the_verified_phone() {
        User user = User.register("Juan", "Ome", new Email("juan@mail.com"), null, NOW);
        user.verifyPhone(new PhoneNumber("+593991112233"), PhoneChannel.SMS, NOW);

        user.delete(NOW);

        assertThat(user.phone()).isNull();
        assertThat(user.isPhoneVerified()).isFalse();
        assertThat(user.phoneChannel()).isNull();
    }

    @Test
    void a_restored_user_marked_verified_without_a_mode_is_not_verified() {
        User user = User.restore(java.util.UUID.randomUUID(), "Juan", "Ome", new Email("juan@mail.com"),
                new PhoneNumber("+573001112233"), true, null, null, true, null, null, null, null, NOW, NOW,
                java.util.Set.of(com.sywater.ms_iam.domain.model.Role.USER));

        assertThat(user.isPhoneVerified()).isFalse();
    }
}
