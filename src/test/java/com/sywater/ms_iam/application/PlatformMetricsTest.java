package com.sywater.ms_iam.application;

import com.sywater.ms_iam.application.dto.PlatformMetricsView;
import com.sywater.ms_iam.application.dto.ServiceTotals;
import com.sywater.ms_iam.application.port.in.RegisterUserUseCase;
import com.sywater.ms_iam.domain.exception.NotAdministratorException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-062: the administrator sees the totals and the active/inactive split of the platform. */
class PlatformMetricsTest {

    private final TestWorld w = new TestWorld();
    private final UUID admin = w.administrator("admin@mail.com");

    private void unverified(String email) {
        w.registration.register(new RegisterUserUseCase.Command("Eva", "Prueba", email, null, TestWorld.PASSWORD));
    }

    @Test
    void users_are_split_into_active_and_inactive_and_deleted_ones_do_not_count() {
        w.verifiedUser("ana@mail.com");
        UUID luis = w.verifiedUser("luis@mail.com");
        UUID borrada = w.verifiedUser("borrada@mail.com");
        unverified("eva@mail.com");
        w.blocking.block(admin, luis, null, TestWorld.CTX);
        w.deletion.deleteAccount(borrada, TestWorld.PASSWORD, TestWorld.CTX);

        PlatformMetricsView.Users users = w.metrics.get(admin).users();

        assertThat(users.total()).isEqualTo(4);        // admin, ana, luis, eva
        assertThat(users.active()).isEqualTo(2);       // admin + ana
        assertThat(users.blocked()).isEqualTo(1);
        assertThat(users.unverified()).isEqualTo(1);
        assertThat(users.inactive()).isEqualTo(2);
        assertThat(users.active() + users.inactive()).isEqualTo(users.total());
    }

    @Test
    void places_and_devices_come_from_their_services() {
        w.metricsSource.places = new ServiceTotals.Places(10);
        w.metricsSource.devices = new ServiceTotals.Devices(8, 5, 6);

        PlatformMetricsView view = w.metrics.get(admin);

        assertThat(view.devices()).isEqualTo(new PlatformMetricsView.Devices(8, 5, 3, 6));
        assertThat(view.places()).isEqualTo(new PlatformMetricsView.Places(10, 6, 4));   // active = has a linked device
        assertThat(view.unavailable()).isEmpty();
        assertThat(view.generatedAt()).isEqualTo(w.now);
    }

    @Test
    void active_places_never_exceed_the_total() {
        w.metricsSource.places = new ServiceTotals.Places(2);
        w.metricsSource.devices = new ServiceTotals.Devices(5, 5, 5);   // out-of-sync counts

        assertThat(w.metrics.get(admin).places()).isEqualTo(new PlatformMetricsView.Places(2, 2, 0));
    }

    @Test
    void an_empty_platform_is_all_zeros() {
        PlatformMetricsView view = w.metrics.get(admin);

        assertThat(view.devices()).isEqualTo(new PlatformMetricsView.Devices(0, 0, 0, 0));
        assertThat(view.places()).isEqualTo(new PlatformMetricsView.Places(0, 0, 0));
    }

    @Test
    void if_ms_places_is_down_the_rest_still_comes() {
        w.metricsSource.devices = new ServiceTotals.Devices(3, 1, 1);
        w.metricsSource.placesDown = true;

        PlatformMetricsView view = w.metrics.get(admin);

        assertThat(view.places()).isNull();
        assertThat(view.devices()).isNotNull();
        assertThat(view.users().total()).isEqualTo(1);
        assertThat(view.unavailable()).containsExactly("places");
    }

    @Test
    void if_ms_device_is_down_the_places_cannot_be_split_either() {
        w.metricsSource.places = new ServiceTotals.Places(4);
        w.metricsSource.devicesDown = true;

        PlatformMetricsView view = w.metrics.get(admin);

        assertThat(view.devices()).isNull();
        assertThat(view.places()).isNull();
        assertThat(view.unavailable()).isEqualTo(List.of("devices", "places"));
        assertThat(view.users()).isNotNull();
    }

    @Test
    void only_an_administrator_can_see_the_metrics() {
        UUID juan = w.verifiedUser("juan@mail.com");

        assertThatThrownBy(() -> w.metrics.get(juan)).isInstanceOf(NotAdministratorException.class);
        assertThatThrownBy(() -> w.metrics.get(UUID.randomUUID())).isInstanceOf(NotAdministratorException.class);
    }
}
