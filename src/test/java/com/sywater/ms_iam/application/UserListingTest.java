package com.sywater.ms_iam.application;

import com.sywater.ms_iam.application.dto.AdminUserView;
import com.sywater.ms_iam.application.dto.PageView;
import com.sywater.ms_iam.application.port.in.ListUsersUseCase;
import com.sywater.ms_iam.application.port.in.RegisterUserUseCase;
import com.sywater.ms_iam.domain.exception.InvalidFilterException;
import com.sywater.ms_iam.domain.exception.NotAdministratorException;
import com.sywater.ms_iam.domain.model.AccountStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-059: the administrator lists registered users. */
class UserListingTest {

    private final TestWorld w = new TestWorld();
    private final UUID admin = w.administrator("admin@mail.com");

    /** Each account is created a minute after the previous one so the order (newest first) is predictable. */
    private UUID verified(String firstName, String email) {
        w.advance(Duration.ofMinutes(1));
        w.registration.register(new RegisterUserUseCase.Command(firstName, "Prueba", email, null, TestWorld.PASSWORD));
        w.registration.verify(email, w.mailbox.lastCode());
        return w.users.findByEmail(new com.sywater.ms_iam.domain.model.Email(email)).orElseThrow().id();
    }

    private void unverified(String firstName, String email) {
        w.advance(Duration.ofMinutes(1));
        w.registration.register(new RegisterUserUseCase.Command(firstName, "Prueba", email, null, TestWorld.PASSWORD));
    }

    private PageView<AdminUserView> list(String search, AccountStatus status, int page, int size) {
        return w.listing.list(admin, search, status, page, size);
    }

    @Test
    void lists_everybody_newest_first_with_their_status_and_no_sensitive_data() {
        verified("Ana", "ana@mail.com");
        UUID luis = verified("Luis", "luis@mail.com");
        unverified("Eva", "eva@mail.com");
        w.blocking.block(admin, luis, null, TestWorld.CTX);

        PageView<AdminUserView> page = list(null, null, 0, 20);

        assertThat(page.totalItems()).isEqualTo(4);   // admin + 3
        assertThat(page.items()).extracting(AdminUserView::firstName).containsExactly("Eva", "Luis", "Ana", "Juan");
        assertThat(page.items()).extracting(AdminUserView::status)
                .containsExactly(AccountStatus.UNVERIFIED, AccountStatus.BLOCKED, AccountStatus.ACTIVE, AccountStatus.ACTIVE);
        assertThat(AdminUserView.class.getRecordComponents()).extracting(c -> c.getName())
                .doesNotContain("password", "passwordHash", "avatarUrl", "roles");
    }

    @Test
    void filters_by_each_status() {
        verified("Ana", "ana@mail.com");
        UUID luis = verified("Luis", "luis@mail.com");
        unverified("Eva", "eva@mail.com");
        w.blocking.block(admin, luis, null, TestWorld.CTX);

        assertThat(list(null, AccountStatus.ACTIVE, 0, 20).items()).extracting(AdminUserView::firstName).containsExactly("Ana", "Juan");
        assertThat(list(null, AccountStatus.BLOCKED, 0, 20).items()).extracting(AdminUserView::firstName).containsExactly("Luis");
        assertThat(list(null, AccountStatus.UNVERIFIED, 0, 20).items()).extracting(AdminUserView::firstName).containsExactly("Eva");
    }

    @Test
    void searches_by_name_or_email_in_any_case_and_combines_with_the_status() {
        verified("Ana", "ana.torres@mail.com");
        verified("Luis", "luis@mail.com");
        unverified("Analia", "otra@mail.com");

        assertThat(list("ANA", null, 0, 20).items()).extracting(AdminUserView::firstName).containsExactly("Analia", "Ana");
        assertThat(list("torres", null, 0, 20).items()).extracting(AdminUserView::email).containsExactly("ana.torres@mail.com");
        assertThat(list("luis prueba", null, 0, 20).items()).hasSize(1);   // full name
        assertThat(list("ana", AccountStatus.UNVERIFIED, 0, 20).items()).extracting(AdminUserView::firstName).containsExactly("Analia");
        assertThat(list("  ", null, 0, 20).totalItems()).isEqualTo(4);     // blank = everybody
        assertThat(list("nadie", null, 0, 20).items()).isEmpty();
    }

    @Test
    void pages_are_stable_and_report_the_totals() {
        for (int i = 1; i <= 5; i++) verified("Persona" + "abcde".charAt(i - 1), "p" + i + "@mail.com");

        PageView<AdminUserView> first = list(null, null, 0, 4);
        PageView<AdminUserView> second = list(null, null, 1, 4);

        assertThat(first.totalItems()).isEqualTo(6);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items()).hasSize(4);
        assertThat(second.items()).hasSize(2);
        assertThat(first.items()).extracting(AdminUserView::id).doesNotContainAnyElementsOf(second.items().stream().map(AdminUserView::id).toList());
        assertThat(list(null, null, 9, 4).items()).isEmpty();
    }

    @Test
    void page_and_size_are_clamped_instead_of_failing() {
        verified("Ana", "ana@mail.com");

        assertThat(list(null, null, -3, 0).size()).isEqualTo(ListUsersUseCase.DEFAULT_SIZE);
        assertThat(list(null, null, 0, 100000).size()).isEqualTo(ListUsersUseCase.MAX_SIZE);
        assertThat(list(null, null, -3, 5).page()).isZero();
    }

    @Test
    void deleted_accounts_never_appear_and_cannot_be_asked_for() {
        UUID ana = verified("Ana", "ana@mail.com");
        w.deletion.deleteAccount(ana, TestWorld.PASSWORD, TestWorld.CTX);

        assertThat(list(null, null, 0, 20).items()).extracting(AdminUserView::firstName).doesNotContain("Ana", "Usuario");
        assertThatThrownBy(() -> list(null, AccountStatus.DELETED, 0, 20)).isInstanceOf(InvalidFilterException.class);
    }

    @Test
    void a_huge_search_text_is_rejected() {
        assertThatThrownBy(() -> list("x".repeat(101), null, 0, 20)).isInstanceOf(InvalidFilterException.class);
    }

    @Test
    void only_an_administrator_can_list() {
        UUID juan = w.verifiedUser("juan@mail.com");

        assertThatThrownBy(() -> w.listing.list(juan, null, null, 0, 20)).isInstanceOf(NotAdministratorException.class);
        assertThatThrownBy(() -> w.listing.list(UUID.randomUUID(), null, null, 0, 20)).isInstanceOf(NotAdministratorException.class);
    }
}
