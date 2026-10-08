package com.sywater.ms_iam.domain;

import com.sywater.ms_iam.domain.exception.CannotBlockSelfException;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import com.sywater.ms_iam.domain.model.AccountStatus;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-060 rules inside the User aggregate. */
class UserBlockingDomainTest {

    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    private static User newUser() {
        return User.register("Juan", "Ome", new Email("juan@mail.com"), null, NOW);
    }

    @Test
    void block_and_unblock_keep_who_and_when_and_are_idempotent() {
        User user = newUser();
        UUID admin = UUID.randomUUID();

        assertThat(user.block(admin, NOW)).isTrue();
        assertThat(user.blockedBy()).isEqualTo(admin);
        assertThat(user.status()).isEqualTo(AccountStatus.BLOCKED);
        assertThat(user.block(UUID.randomUUID(), NOW.plusSeconds(60))).isFalse();   // keeps the FIRST block
        assertThat(user.blockedBy()).isEqualTo(admin);
        assertThat(user.blockedAt()).isEqualTo(NOW);

        assertThat(user.unblock(NOW.plusSeconds(120))).isTrue();
        assertThat(user.isBlocked()).isFalse();
        assertThat(user.blockedBy()).isNull();
        assertThat(user.unblock(NOW.plusSeconds(180))).isFalse();
    }

    @Test
    void nobody_blocks_themselves_nor_a_deleted_account() {
        User user = newUser();
        assertThatThrownBy(() -> user.block(user.id(), NOW)).isInstanceOf(CannotBlockSelfException.class);

        User deleted = newUser();
        deleted.delete(NOW);
        assertThatThrownBy(() -> deleted.block(UUID.randomUUID(), NOW)).isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> deleted.unblock(NOW)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void status_reads_deleted_then_blocked_then_unverified_then_active() {
        User user = newUser();
        assertThat(user.status()).isEqualTo(AccountStatus.UNVERIFIED);

        user.verifyEmail(NOW);
        assertThat(user.status()).isEqualTo(AccountStatus.ACTIVE);

        user.lockUntil(NOW.plusSeconds(900), NOW);   // a temporary lock after failed logins is still ACTIVE
        assertThat(user.status()).isEqualTo(AccountStatus.ACTIVE);

        user.block(UUID.randomUUID(), NOW);
        assertThat(user.status()).isEqualTo(AccountStatus.BLOCKED);

        user.delete(NOW);
        assertThat(user.status()).isEqualTo(AccountStatus.DELETED);
    }
}
