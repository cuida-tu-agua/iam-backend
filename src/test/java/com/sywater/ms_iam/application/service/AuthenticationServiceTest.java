package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.AuthResult;
import com.sywater.ms_iam.application.dto.AuthSettings;
import com.sywater.ms_iam.application.dto.RequestContext;
import com.sywater.ms_iam.application.port.out.ActivityLog;
import com.sywater.ms_iam.application.port.out.CredentialRepository;
import com.sywater.ms_iam.application.port.out.LoginAttemptRepository;
import com.sywater.ms_iam.application.port.out.PasswordHasher;
import com.sywater.ms_iam.application.port.out.RefreshTokenRepository;
import com.sywater.ms_iam.application.port.out.RefreshTokenRepository.StoredRefreshToken;
import com.sywater.ms_iam.application.port.out.SecretHasher;
import com.sywater.ms_iam.application.port.out.TokenRevocationStore;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.AccountBlockedException;
import com.sywater.ms_iam.domain.exception.AccountLockedException;
import com.sywater.ms_iam.domain.exception.AccountNotVerifiedException;
import com.sywater.ms_iam.domain.exception.EmailNotFoundException;
import com.sywater.ms_iam.domain.exception.InvalidRefreshTokenException;
import com.sywater.ms_iam.domain.exception.WrongPasswordException;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.LockoutPolicy;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU-003 / HU-004: login, refresh and logout, with every collaborator replaced by a Mockito mock. */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T15:00:00Z");
    private static final RequestContext CTX = new RequestContext("10.0.0.7", "JUnit");
    private static final String EMAIL = "ana@correo.com";

    @Mock private UserRepository users;
    @Mock private CredentialRepository credentials;
    @Mock private PasswordHasher passwordHasher;
    @Mock private LoginAttemptRepository attempts;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private SecretHasher secretHasher;
    @Mock private TokenRevocationStore revocations;
    @Mock private SessionIssuer sessions;
    @Mock private ActivityLog activity;

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        AuthSettings settings = new AuthSettings(Duration.ofHours(1), Duration.ofDays(7), Duration.ofHours(24),
                Duration.ofMinutes(15), 5, Duration.ofMinutes(1), new LockoutPolicy(5, Duration.ofMinutes(15)), 2_000_000L);
        service = new AuthenticationService(users, credentials, passwordHasher, attempts, refreshTokens, secretHasher,
                revocations, sessions, activity, settings, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static User user(boolean verified, Instant lockedUntil, Instant blockedAt) {
        return User.restore(UUID.randomUUID(), "Ana", "Gómez", new Email(EMAIL), null, null, verified, lockedUntil,
                blockedAt, null, NOW.minusSeconds(86_400), NOW.minusSeconds(86_400), Set.of(Role.USER));
    }

    // ------------------------------------------------------------------ login

    @Test
    @DisplayName("login: con credenciales correctas abre sesión y registra el intento exitoso")
    void givenValidCredentials_whenLogin_thenOpensSessionAndRecordsSuccess() {
        // Arrange
        User ana = user(true, null, null);
        AuthResult expected = new AuthResult("access", NOW.plusSeconds(3600), "refresh", NOW.plusSeconds(600_000), null);
        when(users.findByEmail(new Email(EMAIL))).thenReturn(Optional.of(ana));
        when(credentials.findPasswordHash(ana.id())).thenReturn(Optional.of("HASH"));
        when(passwordHasher.matches("Agua2026!", "HASH")).thenReturn(true);
        when(sessions.open(ana, CTX, NOW)).thenReturn(expected);

        // Act
        AuthResult result = service.login(EMAIL, "Agua2026!", CTX);

        // Assert
        assertThat(result).isSameAs(expected);
        verify(attempts).record(ana.id(), EMAIL, "10.0.0.7", true, null, NOW);
        verify(activity).record(ana.id(), "LOGIN", Map.of(), "10.0.0.7", NOW);
    }

    @Test
    @DisplayName("login: un correo que no existe lanza EmailNotFoundException y deja rastro")
    void givenUnknownEmail_whenLogin_thenThrowsEmailNotFound() {
        // Arrange
        when(users.findByEmail(new Email(EMAIL))).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.login(EMAIL, "Agua2026!", CTX)).isInstanceOf(EmailNotFoundException.class);
        verify(attempts).record(null, EMAIL, "10.0.0.7", false, "EMAIL_NOT_FOUND", NOW);
        verify(sessions, never()).open(any(), any(), any());
    }

    @Test
    @DisplayName("login: una cuenta eliminada se comporta como si no existiera")
    void givenDeletedAccount_whenLogin_thenThrowsEmailNotFound() {
        // Arrange
        User deleted = user(true, null, null);
        deleted.delete(NOW);
        when(users.findByEmail(new Email(EMAIL))).thenReturn(Optional.of(deleted));

        // Act + Assert
        assertThatThrownBy(() -> service.login(EMAIL, "Agua2026!", CTX)).isInstanceOf(EmailNotFoundException.class);
    }

    @Test
    @DisplayName("login: una cuenta bloqueada por el administrador no puede entrar")
    void givenBlockedAccount_whenLogin_thenThrowsAccountBlocked() {
        // Arrange
        User blocked = user(true, null, NOW.minusSeconds(60));
        when(users.findByEmail(new Email(EMAIL))).thenReturn(Optional.of(blocked));

        // Act + Assert
        assertThatThrownBy(() -> service.login(EMAIL, "Agua2026!", CTX)).isInstanceOf(AccountBlockedException.class);
        verify(attempts).record(blocked.id(), EMAIL, "10.0.0.7", false, "ACCOUNT_BLOCKED", NOW);
        verify(passwordHasher, never()).matches(any(), any());
    }

    @Test
    @DisplayName("login: una cuenta con bloqueo temporal vigente lanza AccountLockedException")
    void givenTemporarilyLockedAccount_whenLogin_thenThrowsAccountLocked() {
        // Arrange
        Instant until = NOW.plusSeconds(300);
        User locked = user(true, until, null);
        when(users.findByEmail(new Email(EMAIL))).thenReturn(Optional.of(locked));

        // Act + Assert
        assertThatThrownBy(() -> service.login(EMAIL, "Agua2026!", CTX))
                .isInstanceOfSatisfying(AccountLockedException.class, e -> assertThat(e.lockedUntil()).isEqualTo(until));
        verify(attempts).record(locked.id(), EMAIL, "10.0.0.7", false, "ACCOUNT_LOCKED", NOW);
    }

    @Test
    @DisplayName("login: contraseña incorrecta informa cuántos intentos quedan")
    void givenWrongPassword_whenLogin_thenReportsRemainingAttempts() {
        // Arrange
        User ana = user(true, null, null);
        when(users.findByEmail(new Email(EMAIL))).thenReturn(Optional.of(ana));
        when(credentials.findPasswordHash(ana.id())).thenReturn(Optional.of("HASH"));
        when(passwordHasher.matches("mala", "HASH")).thenReturn(false);
        when(attempts.countFailuresSince(eq(ana.id()), any(Instant.class))).thenReturn(2L);

        // Act + Assert
        assertThatThrownBy(() -> service.login(EMAIL, "mala", CTX))
                .isInstanceOfSatisfying(WrongPasswordException.class, e -> assertThat(e.remainingAttempts()).isEqualTo(3));
        verify(attempts).record(ana.id(), EMAIL, "10.0.0.7", false, "WRONG_PASSWORD", NOW);
        verify(users, never()).update(any());
    }

    @Test
    @DisplayName("login: el quinto fallo bloquea la cuenta 15 minutos")
    void givenFifthWrongPassword_whenLogin_thenLocksAccountForFifteenMinutes() {
        // Arrange
        User ana = user(true, null, null);
        when(users.findByEmail(new Email(EMAIL))).thenReturn(Optional.of(ana));
        when(credentials.findPasswordHash(ana.id())).thenReturn(Optional.of("HASH"));
        when(passwordHasher.matches("mala", "HASH")).thenReturn(false);
        when(attempts.countFailuresSince(eq(ana.id()), any(Instant.class))).thenReturn(5L);

        // Act + Assert
        assertThatThrownBy(() -> service.login(EMAIL, "mala", CTX)).isInstanceOf(AccountLockedException.class);
        assertThat(ana.accountLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        verify(users).update(ana);
        verify(activity).record(eq(ana.id()), eq("ACCOUNT_LOCKED"), anyMap(), eq("10.0.0.7"), eq(NOW));
    }

    @Test
    @DisplayName("login: una contraseña nula se trata como incorrecta, sin romper")
    void givenNullPassword_whenLogin_thenTreatedAsWrongPassword() {
        // Arrange
        User ana = user(true, null, null);
        when(users.findByEmail(new Email(EMAIL))).thenReturn(Optional.of(ana));
        when(credentials.findPasswordHash(ana.id())).thenReturn(Optional.of("HASH"));
        when(passwordHasher.matches("", "HASH")).thenReturn(false);
        when(attempts.countFailuresSince(eq(ana.id()), any(Instant.class))).thenReturn(1L);

        // Act + Assert
        assertThatThrownBy(() -> service.login(EMAIL, null, CTX)).isInstanceOf(WrongPasswordException.class);
    }

    @Test
    @DisplayName("login: la contraseña correcta de una cuenta sin verificar no abre sesión")
    void givenUnverifiedAccount_whenLoginWithRightPassword_thenThrowsNotVerified() {
        // Arrange
        User ana = user(false, null, null);
        when(users.findByEmail(new Email(EMAIL))).thenReturn(Optional.of(ana));
        when(credentials.findPasswordHash(ana.id())).thenReturn(Optional.of("HASH"));
        when(passwordHasher.matches("Agua2026!", "HASH")).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> service.login(EMAIL, "Agua2026!", CTX)).isInstanceOf(AccountNotVerifiedException.class);
        verify(attempts).record(ana.id(), EMAIL, "10.0.0.7", false, "ACCOUNT_NOT_VERIFIED", NOW);
        verify(sessions, never()).open(any(), any(), any());
    }

    // ---------------------------------------------------------------- refresh

    @Test
    @DisplayName("refresh: un token vacío se rechaza sin consultar nada")
    void givenBlankRefreshToken_whenRefresh_thenThrowsInvalidRefreshToken() {
        // Act + Assert
        assertThatThrownBy(() -> service.refresh("  ", CTX)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens, never()).findByHash(any());
    }

    @Test
    @DisplayName("refresh: un token desconocido se rechaza")
    void givenUnknownRefreshToken_whenRefresh_thenThrowsInvalidRefreshToken() {
        // Arrange
        when(secretHasher.hash("tok")).thenReturn("H(tok)");
        when(refreshTokens.findByHash("H(tok)")).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.refresh("tok", CTX)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("refresh: reutilizar un token ya revocado cierra todas las sesiones del usuario")
    void givenReusedRefreshToken_whenRefresh_thenRevokesEverySessionOfTheUser() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(secretHasher.hash("tok")).thenReturn("H(tok)");
        when(refreshTokens.findByHash("H(tok)")).thenReturn(Optional.of(
                new StoredRefreshToken(9L, userId, NOW.plusSeconds(1000), NOW.minusSeconds(10))));

        // Act + Assert
        assertThatThrownBy(() -> service.refresh("tok", CTX)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens).revokeAllForUser(userId, NOW);
        verify(revocations).revokeAllIssuedBefore(userId, NOW);
        verify(activity).record(userId, "REFRESH_TOKEN_REUSED", Map.of(), "10.0.0.7", NOW);
    }

    @Test
    @DisplayName("refresh: un token vencido se rechaza")
    void givenExpiredRefreshToken_whenRefresh_thenThrowsInvalidRefreshToken() {
        // Arrange
        when(secretHasher.hash("tok")).thenReturn("H(tok)");
        when(refreshTokens.findByHash("H(tok)")).thenReturn(Optional.of(
                new StoredRefreshToken(9L, UUID.randomUUID(), NOW.minusSeconds(1), null)));

        // Act + Assert
        assertThatThrownBy(() -> service.refresh("tok", CTX)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(sessions, never()).open(any(), any(), any());
    }

    @Test
    @DisplayName("refresh: si el usuario fue bloqueado no se renueva la sesión")
    void givenBlockedUser_whenRefresh_thenThrowsAccountBlocked() {
        // Arrange
        User blocked = user(true, null, NOW.minusSeconds(5));
        when(secretHasher.hash("tok")).thenReturn("H(tok)");
        when(refreshTokens.findByHash("H(tok)")).thenReturn(Optional.of(
                new StoredRefreshToken(9L, blocked.id(), NOW.plusSeconds(1000), null)));
        when(users.findById(blocked.id())).thenReturn(Optional.of(blocked));

        // Act + Assert
        assertThatThrownBy(() -> service.refresh("tok", CTX)).isInstanceOf(AccountBlockedException.class);
    }

    @Test
    @DisplayName("refresh: dos peticiones paralelas con el mismo token, solo una gana")
    void givenTokenAlreadyConsumedByParallelRequest_whenRefresh_thenThrowsInvalidRefreshToken() {
        // Arrange
        User ana = user(true, null, null);
        when(secretHasher.hash("tok")).thenReturn("H(tok)");
        when(refreshTokens.findByHash("H(tok)")).thenReturn(Optional.of(
                new StoredRefreshToken(9L, ana.id(), NOW.plusSeconds(1000), null)));
        when(users.findById(ana.id())).thenReturn(Optional.of(ana));
        when(refreshTokens.revokeIfActive(9L, NOW)).thenReturn(false);

        // Act + Assert
        assertThatThrownBy(() -> service.refresh("tok", CTX)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(sessions, never()).open(any(), any(), any());
    }

    @Test
    @DisplayName("refresh: un token válido se consume y entrega una sesión nueva")
    void givenValidRefreshToken_whenRefresh_thenRotatesSession() {
        // Arrange
        User ana = user(true, null, null);
        AuthResult expected = new AuthResult("access2", NOW.plusSeconds(3600), "refresh2", NOW.plusSeconds(600_000), null);
        when(secretHasher.hash("tok")).thenReturn("H(tok)");
        when(refreshTokens.findByHash("H(tok)")).thenReturn(Optional.of(
                new StoredRefreshToken(9L, ana.id(), NOW.plusSeconds(1000), null)));
        when(users.findById(ana.id())).thenReturn(Optional.of(ana));
        when(refreshTokens.revokeIfActive(9L, NOW)).thenReturn(true);
        when(sessions.open(ana, CTX, NOW)).thenReturn(expected);

        // Act
        AuthResult result = service.refresh("tok", CTX);

        // Assert
        assertThat(result).isSameAs(expected);
    }

    // ----------------------------------------------------------------- logout

    @Test
    @DisplayName("logout: revoca el access token y el refresh token del propio usuario")
    void givenTokens_whenLogout_thenRevokesBothAndRecordsActivity() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Instant expires = NOW.plusSeconds(1800);
        when(secretHasher.hash("tok")).thenReturn("H(tok)");
        when(refreshTokens.findByHash("H(tok)")).thenReturn(Optional.of(
                new StoredRefreshToken(4L, userId, NOW.plusSeconds(1000), null)));

        // Act
        service.logout(userId, "jti-1", expires, "tok", CTX);

        // Assert
        verify(revocations).revokeToken("jti-1", expires);
        verify(refreshTokens).revokeIfActive(4L, NOW);
        verify(activity).record(userId, "LOGOUT", Map.of(), "10.0.0.7", NOW);
    }

    @Test
    @DisplayName("logout: no revoca un refresh token que pertenece a otro usuario")
    void givenRefreshTokenOfAnotherUser_whenLogout_thenDoesNotRevokeIt() {
        // Arrange
        when(secretHasher.hash("tok")).thenReturn("H(tok)");
        when(refreshTokens.findByHash("H(tok)")).thenReturn(Optional.of(
                new StoredRefreshToken(4L, UUID.randomUUID(), NOW.plusSeconds(1000), null)));

        // Act
        service.logout(UUID.randomUUID(), null, null, "tok", CTX);

        // Assert
        verify(refreshTokens, never()).revokeIfActive(anyLong(), any());
        verify(revocations, never()).revokeToken(any(), any());
    }
}
