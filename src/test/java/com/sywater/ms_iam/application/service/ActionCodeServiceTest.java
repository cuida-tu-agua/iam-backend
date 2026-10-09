package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.CodeSent;
import com.sywater.ms_iam.application.dto.RequestContext;
import com.sywater.ms_iam.application.port.out.ActivityLog;
import com.sywater.ms_iam.application.port.out.NotificationSender;
import com.sywater.ms_iam.application.port.out.OneTimeCodeRepository.Purpose;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.AccountBlockedException;
import com.sywater.ms_iam.domain.exception.UnknownActionException;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.Role;
import com.sywater.ms_iam.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Código de un solo uso para acciones sensibles (cerrar la válvula). */
@ExtendWith(MockitoExtension.class)
class ActionCodeServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T15:00:00Z");
    private static final Duration VALID_FOR = Duration.ofMinutes(5);
    private static final RequestContext CTX = new RequestContext("10.0.0.7", "JUnit");

    @Mock private UserRepository users;
    @Mock private OneTimeCodes codes;
    @Mock private NotificationSender notifications;
    @Mock private ActivityLog activity;

    private ActionCodeService service;

    @BeforeEach
    void setUp() {
        service = new ActionCodeService(users, codes, notifications, activity, VALID_FOR, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static User ana(Instant blockedAt, Instant deletedAt) {
        return User.restore(UUID.randomUUID(), "Ana", "Gómez", new Email("ana@correo.com"), null, null, true, null,
                blockedAt, deletedAt, NOW.minusSeconds(86_400), NOW.minusSeconds(86_400), Set.of(Role.USER));
    }

    @Test
    @DisplayName("requestCode: emite el código, lo envía por correo y devuelve el correo enmascarado")
    void givenActiveUser_whenRequestCode_thenIssuesAndSendsEmail() {
        // Arrange
        User user = ana(null, null);
        when(users.findById(user.id())).thenReturn(Optional.of(user));
        when(codes.issue(Purpose.VALVE_CLOSE, user.id(), VALID_FOR, NOW, true)).thenReturn("123456");

        // Act
        CodeSent sent = service.requestCode(user.id(), "valve_close", CTX);

        // Assert
        assertThat(sent.maskedEmail()).isEqualTo("an*@correo.com");
        assertThat(sent.expiresAt()).isEqualTo(NOW.plus(VALID_FOR));
        verify(notifications).sendActionCode(user.email(), "Ana", "cerrar la válvula de agua", "123456", VALID_FOR);
        verify(activity).record(user.id(), "ACTION_CODE_REQUESTED", Map.of("action", "VALVE_CLOSE"), "10.0.0.7", NOW);
    }

    @Test
    @DisplayName("requestCode: una acción desconocida lanza UnknownActionException")
    void givenUnknownAction_whenRequestCode_thenThrowsUnknownAction() {
        // Act + Assert
        assertThatThrownBy(() -> service.requestCode(UUID.randomUUID(), "SELF_DESTRUCT", CTX))
                .isInstanceOf(UnknownActionException.class);
        verify(notifications, never()).sendActionCode(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("requestCode: una acción nula se trata como desconocida")
    void givenNullAction_whenRequestCode_thenThrowsUnknownAction() {
        // Act + Assert
        assertThatThrownBy(() -> service.requestCode(UUID.randomUUID(), null, CTX))
                .isInstanceOf(UnknownActionException.class);
    }

    @Test
    @DisplayName("requestCode: un usuario inexistente o eliminado lanza UserNotFoundException")
    void givenDeletedUser_whenRequestCode_thenThrowsUserNotFound() {
        // Arrange
        User deleted = ana(null, NOW.minusSeconds(10));
        when(users.findById(deleted.id())).thenReturn(Optional.of(deleted));

        // Act + Assert
        assertThatThrownBy(() -> service.requestCode(deleted.id(), "VALVE_CLOSE", CTX))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("requestCode: un usuario bloqueado no recibe códigos")
    void givenBlockedUser_whenRequestCode_thenThrowsAccountBlocked() {
        // Arrange
        User blocked = ana(NOW.minusSeconds(10), null);
        when(users.findById(blocked.id())).thenReturn(Optional.of(blocked));

        // Act + Assert
        assertThatThrownBy(() -> service.requestCode(blocked.id(), "VALVE_CLOSE", CTX))
                .isInstanceOf(AccountBlockedException.class);
        verify(codes, never()).issue(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("verifyCode: verifica el código y registra la actividad")
    void givenActiveUser_whenVerifyCode_thenVerifiesAndRecordsActivity() {
        // Arrange
        User user = ana(null, null);
        when(users.findById(user.id())).thenReturn(Optional.of(user));

        // Act
        service.verifyCode(user.id(), "VALVE_CLOSE", "123456", CTX);

        // Assert
        verify(codes).verify(Purpose.VALVE_CLOSE, user.id(), "123456", NOW);
        verify(activity).record(user.id(), "ACTION_CODE_VERIFIED", Map.of("action", "VALVE_CLOSE"), "10.0.0.7", NOW);
    }
}
