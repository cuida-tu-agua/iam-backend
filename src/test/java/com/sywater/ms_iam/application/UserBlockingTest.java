package com.sywater.ms_iam.application;

import com.sywater.ms_iam.application.dto.AdminUserView;
import com.sywater.ms_iam.domain.exception.AccountBlockedException;
import com.sywater.ms_iam.domain.exception.CannotBlockSelfException;
import com.sywater.ms_iam.domain.exception.InvalidRefreshTokenException;
import com.sywater.ms_iam.domain.exception.NotAdministratorException;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import com.sywater.ms_iam.domain.model.AccountStatus;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.Role;
import com.sywater.ms_iam.domain.model.User;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-060: an administrator blocks and unblocks accounts. */
class UserBlockingTest {

    private final TestWorld w = new TestWorld();

    /** An account that already holds the ADMIN role (in production it is promoted in the database). */
    private UUID administrator(String email) {
        UUID id = w.verifiedUser(email);
        User user = w.users.byId.get(id);
        w.users.byId.put(id, User.restore(id, user.firstName(), user.lastName(), user.email(), user.phone(), null,
                true, null, null, null, user.createdAt(), user.updatedAt(), EnumSet.of(Role.USER, Role.ADMIN)));
        return id;
    }

    @Test
    void blocking_records_who_and_when_and_closes_every_session_at_once() {
        UUID admin = administrator("admin@mail.com");
        UUID juan = w.verifiedUser("juan@mail.com");
        w.auth.login("juan@mail.com", TestWorld.PASSWORD, TestWorld.CTX);
        assertThat(w.refreshTokens.active(juan)).isEqualTo(1);

        AdminUserView view = w.blocking.block(admin, juan, "Uso indebido", TestWorld.CTX);

        assertThat(view.status()).isEqualTo(AccountStatus.BLOCKED);
        assertThat(view.blockedBy()).isEqualTo(admin);
        assertThat(view.blockedAt()).isEqualTo(w.now);
        assertThat(w.refreshTokens.active(juan)).isZero();
        assertThat(w.revocations.revokedBefore).containsKey(juan);   // access tokens already issued die too
    }

    @Test
    void a_blocked_user_cannot_log_in_or_refresh_and_gets_the_explanation() {
        UUID admin = administrator("admin@mail.com");
        UUID juan = w.verifiedUser("juan@mail.com");
        var session = w.auth.login("juan@mail.com", TestWorld.PASSWORD, TestWorld.CTX);

        w.blocking.block(admin, juan, null, TestWorld.CTX);

        assertThatThrownBy(() -> w.auth.login("juan@mail.com", TestWorld.PASSWORD, TestWorld.CTX))
                .isInstanceOf(AccountBlockedException.class);
        assertThatThrownBy(() -> w.auth.refresh(session.refreshToken(), TestWorld.CTX))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void the_action_is_logged_for_the_administrator_and_for_the_user() {
        UUID admin = administrator("admin@mail.com");
        UUID juan = w.verifiedUser("juan@mail.com");
        w.activity.entries.clear();

        w.blocking.block(admin, juan, "Uso indebido", TestWorld.CTX);

        var byAdmin = w.activity.entries.stream().filter(e -> e.action().equals("USER_BLOCKED")).findFirst().orElseThrow();
        assertThat(byAdmin.userId()).isEqualTo(admin);
        assertThat(byAdmin.metadata()).containsEntry("targetUserId", juan.toString()).containsEntry("reason", "Uso indebido");
        var onUser = w.activity.entries.stream().filter(e -> e.action().equals("ACCOUNT_BLOCKED")).findFirst().orElseThrow();
        assertThat(onUser.userId()).isEqualTo(juan);
        assertThat(onUser.metadata()).containsEntry("administratorId", admin.toString());
    }

    @Test
    void blocking_twice_changes_nothing_and_logs_once() {
        UUID admin = administrator("admin@mail.com");
        UUID other = administrator("otro@mail.com");
        UUID juan = w.verifiedUser("juan@mail.com");
        w.blocking.block(admin, juan, null, TestWorld.CTX);
        w.advance(java.time.Duration.ofHours(1));

        AdminUserView again = w.blocking.block(other, juan, null, TestWorld.CTX);

        assertThat(again.blockedBy()).isEqualTo(admin);   // who did it FIRST
        assertThat(w.activity.actions.stream().filter("USER_BLOCKED"::equals).count()).isEqualTo(1);
    }

    @Test
    void unblocking_lets_the_user_in_again() {
        UUID admin = administrator("admin@mail.com");
        UUID juan = w.verifiedUser("juan@mail.com");
        w.blocking.block(admin, juan, null, TestWorld.CTX);
        w.advance(java.time.Duration.ofMinutes(5));

        AdminUserView view = w.blocking.unblock(admin, juan, "Resuelto", TestWorld.CTX);

        assertThat(view.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(view.blockedAt()).isNull();
        assertThat(view.blockedBy()).isNull();
        assertThat(w.activity.actions).contains("USER_UNBLOCKED", "ACCOUNT_UNBLOCKED");
        assertThat(w.auth.login("juan@mail.com", TestWorld.PASSWORD, TestWorld.CTX).accessToken()).isNotNull();
    }

    @Test
    void only_an_administrator_can_do_it_even_with_an_admin_token() {
        UUID juan = w.verifiedUser("juan@mail.com");
        UUID ana = w.verifiedUser("ana@mail.com");

        assertThatThrownBy(() -> w.blocking.block(juan, ana, null, TestWorld.CTX)).isInstanceOf(NotAdministratorException.class);
        assertThatThrownBy(() -> w.blocking.unblock(juan, ana, null, TestWorld.CTX)).isInstanceOf(NotAdministratorException.class);
        assertThatThrownBy(() -> w.blocking.block(UUID.randomUUID(), ana, null, TestWorld.CTX)).isInstanceOf(NotAdministratorException.class);
    }

    @Test
    void an_administrator_who_was_blocked_loses_the_power_immediately() {
        UUID boss = administrator("jefe@mail.com");
        UUID admin = administrator("admin@mail.com");
        UUID juan = w.verifiedUser("juan@mail.com");
        w.blocking.block(boss, admin, null, TestWorld.CTX);

        assertThatThrownBy(() -> w.blocking.block(admin, juan, null, TestWorld.CTX)).isInstanceOf(NotAdministratorException.class);
    }

    @Test
    void nobody_blocks_themselves_and_unknown_or_deleted_accounts_are_not_found() {
        UUID admin = administrator("admin@mail.com");
        UUID juan = w.verifiedUser("juan@mail.com");
        w.deletion.deleteAccount(juan, TestWorld.PASSWORD, TestWorld.CTX);

        assertThatThrownBy(() -> w.blocking.block(admin, admin, null, TestWorld.CTX)).isInstanceOf(CannotBlockSelfException.class);
        assertThatThrownBy(() -> w.blocking.block(admin, juan, null, TestWorld.CTX)).isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> w.blocking.block(admin, UUID.randomUUID(), null, TestWorld.CTX)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void a_long_reason_is_cut_before_it_reaches_the_log() {
        UUID admin = administrator("admin@mail.com");
        UUID juan = w.verifiedUser("juan@mail.com");

        w.blocking.block(admin, juan, "x".repeat(500), TestWorld.CTX);

        var entry = w.activity.entries.stream().filter(e -> e.action().equals("USER_BLOCKED")).findFirst().orElseThrow();
        assertThat(entry.metadata().get("reason")).hasSize(200);
        assertThat(new Email("juan@mail.com")).isNotNull();
    }
}
