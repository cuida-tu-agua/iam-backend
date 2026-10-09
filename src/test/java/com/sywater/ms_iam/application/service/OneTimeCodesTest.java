package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.AuthSettings;
import com.sywater.ms_iam.application.port.out.OneTimeCodeRepository;
import com.sywater.ms_iam.application.port.out.OneTimeCodeRepository.Purpose;
import com.sywater.ms_iam.application.port.out.OneTimeCodeRepository.StoredCode;
import com.sywater.ms_iam.application.port.out.SecretGenerator;
import com.sywater.ms_iam.application.port.out.SecretHasher;
import com.sywater.ms_iam.domain.exception.CodeExpiredException;
import com.sywater.ms_iam.domain.exception.CodeRecentlySentException;
import com.sywater.ms_iam.domain.exception.InvalidCodeException;
import com.sywater.ms_iam.domain.model.LockoutPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Códigos de un solo uso (verificación de correo, recuperación, cierre de válvula). */
@ExtendWith(MockitoExtension.class)
class OneTimeCodesTest {

    private static final Instant NOW = Instant.parse("2026-09-29T15:00:00Z");
    private static final UUID USER = UUID.randomUUID();

    @Mock private OneTimeCodeRepository repository;
    @Mock private SecretGenerator generator;
    @Mock private SecretHasher hasher;

    private OneTimeCodes codes;

    @BeforeEach
    void setUp() {
        AuthSettings settings = new AuthSettings(Duration.ofHours(1), Duration.ofDays(7), Duration.ofHours(24),
                Duration.ofMinutes(15), 5, Duration.ofMinutes(1), new LockoutPolicy(5, Duration.ofMinutes(15)), 2_000_000L);
        codes = new OneTimeCodes(repository, generator, hasher, settings);
    }

    @Test
    @DisplayName("issue: guarda solo el hash del código y devuelve el código en claro")
    void givenNoCooldown_whenIssue_thenStoresHashAndReturnsCode() {
        // Arrange
        when(generator.sixDigitCode()).thenReturn("123456");
        when(hasher.hash(USER + ":123456")).thenReturn("HASH");

        // Act
        String code = codes.issue(Purpose.EMAIL_VERIFICATION, USER, Duration.ofHours(24), NOW, false);

        // Assert
        assertThat(code).isEqualTo("123456");
        verify(repository).issue(Purpose.EMAIL_VERIFICATION, USER, "HASH", NOW.plus(Duration.ofHours(24)), NOW);
        verify(repository, never()).lastIssuedAt(any(), any());
    }

    @Test
    @DisplayName("issue: pedir otro código antes del tiempo de espera lanza CodeRecentlySentException")
    void givenCodeSentTenSecondsAgo_whenIssueWithCooldown_thenThrowsWithRemainingWait() {
        // Arrange
        when(repository.lastIssuedAt(Purpose.PASSWORD_RESET, USER)).thenReturn(Optional.of(NOW.minusSeconds(10)));

        // Act + Assert
        assertThatThrownBy(() -> codes.issue(Purpose.PASSWORD_RESET, USER, Duration.ofMinutes(15), NOW, true))
                .isInstanceOfSatisfying(CodeRecentlySentException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(50)));
        verify(repository, never()).issue(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("issue: pasado el tiempo de espera sí emite otro código")
    void givenCooldownElapsed_whenIssueWithCooldown_thenIssuesNewCode() {
        // Arrange
        when(repository.lastIssuedAt(Purpose.PASSWORD_RESET, USER)).thenReturn(Optional.of(NOW.minusSeconds(61)));
        when(generator.sixDigitCode()).thenReturn("654321");
        when(hasher.hash(USER + ":654321")).thenReturn("H2");

        // Act
        String code = codes.issue(Purpose.PASSWORD_RESET, USER, Duration.ofMinutes(15), NOW, true);

        // Assert
        assertThat(code).isEqualTo("654321");
    }

    @Test
    @DisplayName("verify: un código correcto se consume")
    void givenCorrectCode_whenVerify_thenConsumesIt() {
        // Arrange
        when(repository.findActive(Purpose.VALVE_CLOSE, USER, NOW))
                .thenReturn(Optional.of(new StoredCode(7L, "HASH", NOW.plusSeconds(60), 0)));
        when(hasher.matches(USER + ":123456", "HASH")).thenReturn(true);
        when(repository.consume(Purpose.VALVE_CLOSE, 7L, NOW)).thenReturn(true);

        // Act
        codes.verify(Purpose.VALVE_CLOSE, USER, " 123456 ", NOW);

        // Assert
        verify(repository).consume(Purpose.VALVE_CLOSE, 7L, NOW);
    }

    @Test
    @DisplayName("verify: sin código vigente lanza CodeExpiredException")
    void givenNoActiveCode_whenVerify_thenThrowsCodeExpired() {
        // Arrange
        when(repository.findActive(Purpose.VALVE_CLOSE, USER, NOW)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> codes.verify(Purpose.VALVE_CLOSE, USER, "123456", NOW))
                .isInstanceOf(CodeExpiredException.class);
    }

    @Test
    @DisplayName("verify: un código que ya agotó sus intentos se considera vencido")
    void givenCodeWithMaxFailedAttempts_whenVerify_thenThrowsCodeExpired() {
        // Arrange
        when(repository.findActive(Purpose.VALVE_CLOSE, USER, NOW))
                .thenReturn(Optional.of(new StoredCode(7L, "HASH", NOW.plusSeconds(60), 5)));

        // Act + Assert
        assertThatThrownBy(() -> codes.verify(Purpose.VALVE_CLOSE, USER, "123456", NOW))
                .isInstanceOf(CodeExpiredException.class);
        verify(repository, never()).consume(any(), anyLong(), any());
    }

    @Test
    @DisplayName("verify: un código incorrecto cuenta el fallo e informa los intentos restantes")
    void givenWrongCode_whenVerify_thenRegistersFailureAndReportsRemaining() {
        // Arrange
        when(repository.findActive(Purpose.EMAIL_VERIFICATION, USER, NOW))
                .thenReturn(Optional.of(new StoredCode(3L, "HASH", NOW.plusSeconds(60), 1)));
        when(hasher.matches(USER + ":999999", "HASH")).thenReturn(false);
        when(repository.registerFailedAttempt(Purpose.EMAIL_VERIFICATION, 3L)).thenReturn(2);

        // Act + Assert
        assertThatThrownBy(() -> codes.verify(Purpose.EMAIL_VERIFICATION, USER, "999999", NOW))
                .isInstanceOfSatisfying(InvalidCodeException.class, e -> assertThat(e.remainingAttempts()).isEqualTo(3));
    }

    @ParameterizedTest(name = "formato inválido \"{0}\"")
    @ValueSource(strings = {"", "12345", "1234567", "abcdef", "12 456"})
    @DisplayName("verify: un código que no tiene 6 dígitos falla sin comparar el hash")
    void givenMalformedCode_whenVerify_thenFailsWithoutComparingHash(String malformed) {
        // Arrange
        when(repository.findActive(Purpose.EMAIL_VERIFICATION, USER, NOW))
                .thenReturn(Optional.of(new StoredCode(3L, "HASH", NOW.plusSeconds(60), 0)));
        when(repository.registerFailedAttempt(Purpose.EMAIL_VERIFICATION, 3L)).thenReturn(1);

        // Act + Assert
        assertThatThrownBy(() -> codes.verify(Purpose.EMAIL_VERIFICATION, USER, malformed, NOW))
                .isInstanceOf(InvalidCodeException.class);
        verify(hasher, never()).matches(any(), any());
    }

    @Test
    @DisplayName("verify: si otra petición ya consumió el código, este intento falla")
    void givenCodeConsumedByParallelRequest_whenVerify_thenThrowsCodeExpired() {
        // Arrange
        when(repository.findActive(Purpose.VALVE_CLOSE, USER, NOW))
                .thenReturn(Optional.of(new StoredCode(7L, "HASH", NOW.plusSeconds(60), 0)));
        when(hasher.matches(USER + ":123456", "HASH")).thenReturn(true);
        when(repository.consume(Purpose.VALVE_CLOSE, 7L, NOW)).thenReturn(false);

        // Act + Assert
        assertThatThrownBy(() -> codes.verify(Purpose.VALVE_CLOSE, USER, "123456", NOW))
                .isInstanceOf(CodeExpiredException.class);
    }
}
