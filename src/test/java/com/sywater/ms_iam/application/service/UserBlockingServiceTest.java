package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.AdminUserView;
import com.sywater.ms_iam.application.dto.RequestContext;
import com.sywater.ms_iam.application.port.out.ActivityLog;
import com.sywater.ms_iam.application.port.out.RefreshTokenRepository;
import com.sywater.ms_iam.application.port.out.TokenRevocationStore;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.CannotBlockSelfException;
import com.sywater.ms_iam.domain.exception.NotAdministratorException;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import com.sywater.ms_iam.domain.model.AccountStatus;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.Role;
import com.sywater.ms_iam.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU-060: un administrador bloquea y desbloquea cuentas. */
@ExtendWith(MockitoExtension.class)
class UserBlockingServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T15:00:00Z");
    private static final RequestContext CTX = new RequestContext("10.0.0.7", "JUnit");

    @Mock private UserRepository users;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private TokenRevocationStore revocations;
    @Mock private ActivityLog activity;

    @Captor private ArgumentCaptor<Map<String, String>> metadata;

    private UserBlockingService service;

    @BeforeEach
    void setUp() {
        service = new UserBlockingService(users, refreshTokens, revocations, activity, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static User account(String email, Set<Role> roles, Instant blockedAt) {
        return User.restore(UUID.randomUUID(), "Ana", "Gómez", new Email(email), null, null, true, null, blockedAt, null,
                NOW.minusSeconds(86_400), NOW.minusSeconds(86_400), roles);
    }

    @Test
    @DisplayName("block: bloquea la cuenta, cierra sus sesiones y deja dos filas de auditoría")
    void givenAdminAndActiveUser_whenBlock_thenBlocksRevokesSessionsAndAudits() {
        // Arrange
        User admin = account("admin@correo.com", Set.of(Role.USER, Role.ADMIN), null);
        User target = account("ana@correo.com", Set.of(Role.USER), null);
        when(users.findById(admin.id())).thenReturn(Optional.of(admin));
        when(users.findById(target.id())).thenReturn(Optional.of(target));

        // Act
        AdminUserView view = service.block(admin.id(), target.id(), "  abuso  ", CTX);

        // Assert
        assertThat(view.status()).isEqualTo(AccountStatus.BLOCKED);
        assertThat(view.blockedBy()).isEqualTo(admin.id());
        verify(users).update(target);
        verify(refreshTokens).revokeAllForUser(target.id(), NOW);
        verify(revocations).revokeAllIssuedBefore(target.id(), NOW);
        verify(activity).record(eq(admin.id()), eq("USER_BLOCKED"), metadata.capture(), eq("10.0.0.7"), eq(NOW));
        assertThat(metadata.getValue()).containsEntry("reason", "abuso").containsEntry("targetUserId", target.id().toString());
        verify(activity).record(eq(target.id()), eq("ACCOUNT_BLOCKED"), any(), eq("10.0.0.7"), eq(NOW));
    }

    @Test
    @DisplayName("block: bloquear una cuenta ya bloqueada no cambia nada (idempotente)")
    void givenAlreadyBlockedUser_whenBlock_thenNothingChanges() {
        // Arrange
        User admin = account("admin@correo.com", Set.of(Role.ADMIN), null);
        User target = account("ana@correo.com", Set.of(Role.USER), NOW.minusSeconds(500));
        when(users.findById(admin.id())).thenReturn(Optional.of(admin));
        when(users.findById(target.id())).thenReturn(Optional.of(target));

        // Act
        AdminUserView view = service.block(admin.id(), target.id(), null, CTX);

        // Assert
        assertThat(view.blockedAt()).isEqualTo(NOW.minusSeconds(500));
        verify(users, never()).update(any());
        verify(activity, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("block: el motivo se recorta a 200 caracteres")
    void givenVeryLongReason_whenBlock_thenReasonIsTruncated() {
        // Arrange
        User admin = account("admin@correo.com", Set.of(Role.ADMIN), null);
        User target = account("ana@correo.com", Set.of(Role.USER), null);
        when(users.findById(admin.id())).thenReturn(Optional.of(admin));
        when(users.findById(target.id())).thenReturn(Optional.of(target));

        // Act
        service.block(admin.id(), target.id(), "x".repeat(500), CTX);

        // Assert
        verify(activity).record(eq(admin.id()), eq("USER_BLOCKED"), metadata.capture(), any(), any());
        assertThat(metadata.getValue().get("reason")).hasSize(UserBlockingService.REASON_MAX);
    }

    @Test
    @DisplayName("block: quien no es administrador no puede bloquear")
    void givenNonAdministrator_whenBlock_thenThrowsNotAdministrator() {
        // Arrange
        User plain = account("ana@correo.com", Set.of(Role.USER), null);
        when(users.findById(plain.id())).thenReturn(Optional.of(plain));

        // Act + Assert
        assertThatThrownBy(() -> service.block(plain.id(), UUID.randomUUID(), null, CTX))
                .isInstanceOf(NotAdministratorException.class);
        verify(users, never()).update(any());
    }

    @Test
    @DisplayName("block: un administrador que ya está bloqueado pierde el poder de inmediato")
    void givenBlockedAdministrator_whenBlock_thenThrowsNotAdministrator() {
        // Arrange
        User admin = account("admin@correo.com", Set.of(Role.ADMIN), NOW.minusSeconds(1));
        when(users.findById(admin.id())).thenReturn(Optional.of(admin));

        // Act + Assert
        assertThatThrownBy(() -> service.block(admin.id(), UUID.randomUUID(), null, CTX))
                .isInstanceOf(NotAdministratorException.class);
    }

    @Test
    @DisplayName("block: un usuario inexistente lanza UserNotFoundException")
    void givenUnknownTarget_whenBlock_thenThrowsUserNotFound() {
        // Arrange
        User admin = account("admin@correo.com", Set.of(Role.ADMIN), null);
        UUID missing = UUID.randomUUID();
        when(users.findById(admin.id())).thenReturn(Optional.of(admin));
        when(users.findById(missing)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.block(admin.id(), missing, null, CTX)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("block: nadie puede bloquear su propia cuenta")
    void givenAdministratorTargetingSelf_whenBlock_thenThrowsCannotBlockSelf() {
        // Arrange
        User admin = account("admin@correo.com", Set.of(Role.ADMIN), null);
        when(users.findById(admin.id())).thenReturn(Optional.of(admin));

        // Act + Assert
        assertThatThrownBy(() -> service.block(admin.id(), admin.id(), null, CTX))
                .isInstanceOf(CannotBlockSelfException.class);
    }

    @Test
    @DisplayName("unblock: reactiva una cuenta bloqueada y audita")
    void givenBlockedUser_whenUnblock_thenAccountIsActiveAgain() {
        // Arrange
        User admin = account("admin@correo.com", Set.of(Role.ADMIN), null);
        User target = account("ana@correo.com", Set.of(Role.USER), NOW.minusSeconds(500));
        when(users.findById(admin.id())).thenReturn(Optional.of(admin));
        when(users.findById(target.id())).thenReturn(Optional.of(target));

        // Act
        AdminUserView view = service.unblock(admin.id(), target.id(), "error", CTX);

        // Assert
        assertThat(view.status()).isEqualTo(AccountStatus.ACTIVE);
        verify(users).update(target);
        verify(activity).record(eq(admin.id()), eq("USER_UNBLOCKED"), any(), any(), eq(NOW));
        verify(activity).record(eq(target.id()), eq("ACCOUNT_UNBLOCKED"), any(), any(), eq(NOW));
    }

    @Test
    @DisplayName("unblock: desbloquear una cuenta que no estaba bloqueada no hace nada")
    void givenActiveUser_whenUnblock_thenNothingChanges() {
        // Arrange
        User admin = account("admin@correo.com", Set.of(Role.ADMIN), null);
        User target = account("ana@correo.com", Set.of(Role.USER), null);
        when(users.findById(admin.id())).thenReturn(Optional.of(admin));
        when(users.findById(target.id())).thenReturn(Optional.of(target));

        // Act
        service.unblock(admin.id(), target.id(), null, CTX);

        // Assert
        verify(users, never()).update(any());
    }
}
