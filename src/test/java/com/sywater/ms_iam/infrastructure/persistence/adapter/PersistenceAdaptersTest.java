package com.sywater.ms_iam.infrastructure.persistence.adapter;

import com.sywater.ms_iam.application.port.out.OneTimeCodeRepository.Purpose;
import com.sywater.ms_iam.application.port.out.RefreshTokenRepository.StoredRefreshToken;
import com.sywater.ms_iam.infrastructure.persistence.jpa.SpringActivityLogJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adaptadores de sesión y códigos contra H2 en memoria: intentos de login, refresh tokens, credenciales,
 * bitácora de actividad y códigos de un solo uso. Está en el mismo paquete que los adaptadores para poder
 * probar también el método de ayuda {@code toJson}.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:iam-test-adapters;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS security",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.use_nationalized_character_data=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaLoginAttemptRepositoryAdapter.class, JpaRefreshTokenRepositoryAdapter.class,
        JpaCredentialRepositoryAdapter.class, JpaActivityLogAdapter.class, JpaOneTimeCodeRepositoryAdapter.class})
class PersistenceAdaptersTest {

    private static final Instant NOW = Instant.parse("2026-09-29T15:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired private JpaLoginAttemptRepositoryAdapter attempts;
    @Autowired private JpaRefreshTokenRepositoryAdapter refreshTokens;
    @Autowired private JpaCredentialRepositoryAdapter credentials;
    @Autowired private JpaActivityLogAdapter activity;
    @Autowired private JpaOneTimeCodeRepositoryAdapter codes;
    @Autowired private SpringActivityLogJpaRepository activityRows;

    // ---------------------------------------------------------- intentos de login

    @Test
    @DisplayName("login attempts: cuenta solo las contraseñas incorrectas posteriores al corte")
    void givenMixedAttempts_whenCountFailuresSince_thenCountsOnlyRecentWrongPasswords() {
        // Arrange
        UUID user = UUID.randomUUID();
        attempts.record(user, "ana@correo.com", "1.1.1.1", false, "WRONG_PASSWORD", NOW.minusSeconds(600));
        attempts.record(user, "ana@correo.com", "1.1.1.1", false, "WRONG_PASSWORD", NOW.minusSeconds(60));
        attempts.record(user, "ana@correo.com", "1.1.1.1", false, "ACCOUNT_LOCKED", NOW.minusSeconds(30));
        attempts.record(user, "ana@correo.com", "1.1.1.1", true, null, NOW.minusSeconds(10));

        // Act
        long failures = attempts.countFailuresSince(user, NOW.minusSeconds(300));

        // Assert
        assertThat(failures).isEqualTo(1);
    }

    @Test
    @DisplayName("login attempts: lastSuccessAt devuelve el último éxito, o vacío si nunca hubo")
    void givenSuccesses_whenLastSuccessAt_thenReturnsLatestOrEmpty() {
        // Arrange
        UUID user = UUID.randomUUID();
        attempts.record(user, "ana@correo.com", "1.1.1.1", true, null, NOW.minusSeconds(500));
        attempts.record(user, "ana@correo.com", "1.1.1.1", true, null, NOW.minusSeconds(100));

        // Act + Assert
        assertThat(attempts.lastSuccessAt(user)).contains(NOW.minusSeconds(100));
        assertThat(attempts.lastSuccessAt(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("login attempts: un correo de más de 320 caracteres se recorta en vez de fallar")
    void givenVeryLongEmail_whenRecord_thenItIsTruncated() {
        // Arrange
        UUID user = UUID.randomUUID();
        String huge = "a".repeat(400) + "@correo.com";

        // Act
        attempts.record(user, huge, "1.1.1.1", false, "WRONG_PASSWORD", NOW);

        // Assert
        assertThat(attempts.countFailuresSince(user, NOW.minusSeconds(1))).isEqualTo(1);
    }

    // ------------------------------------------------------------ refresh tokens

    @Test
    @DisplayName("refresh tokens: se guarda por hash y se recupera sin revocar")
    void givenSavedToken_whenFindByHash_thenReturnsActiveToken() {
        // Arrange
        UUID user = UUID.randomUUID();
        refreshTokens.save(user, "HASH-1", "Pixel 8", NOW.plusSeconds(1000), NOW);

        // Act
        Optional<StoredRefreshToken> found = refreshTokens.findByHash("HASH-1");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().userId()).isEqualTo(user);
        assertThat(found.get().revokedAt()).isNull();
        assertThat(refreshTokens.findByHash("OTRO")).isEmpty();
    }

    @Test
    @DisplayName("refresh tokens: revokeIfActive solo funciona la primera vez")
    void givenActiveToken_whenRevokeIfActiveTwice_thenOnlyFirstSucceeds() {
        // Arrange
        refreshTokens.save(UUID.randomUUID(), "HASH-2", null, NOW.plusSeconds(1000), NOW);
        long id = refreshTokens.findByHash("HASH-2").orElseThrow().id();

        // Act
        boolean first = refreshTokens.revokeIfActive(id, NOW);
        boolean second = refreshTokens.revokeIfActive(id, NOW);

        // Assert
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(refreshTokens.findByHash("HASH-2").orElseThrow().revokedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("refresh tokens: revokeAllForUser revoca solo los tokens activos de ese usuario")
    void givenTokensOfTwoUsers_whenRevokeAllForUser_thenOnlyThatUserIsAffected() {
        // Arrange
        UUID ana = UUID.randomUUID();
        UUID beto = UUID.randomUUID();
        refreshTokens.save(ana, "A1", null, NOW.plusSeconds(1000), NOW);
        refreshTokens.save(ana, "A2", null, NOW.plusSeconds(1000), NOW);
        refreshTokens.save(beto, "B1", null, NOW.plusSeconds(1000), NOW);

        // Act
        int revoked = refreshTokens.revokeAllForUser(ana, NOW);

        // Assert
        assertThat(revoked).isEqualTo(2);
        assertThat(refreshTokens.findByHash("A1").orElseThrow().revokedAt()).isNotNull();
        assertThat(refreshTokens.findByHash("B1").orElseThrow().revokedAt()).isNull();
    }

    // ------------------------------------------------------------- credenciales

    @Test
    @DisplayName("credentials: guarda el hash y al guardar de nuevo lo reemplaza (no duplica)")
    void givenExistingCredential_whenSavePasswordHashAgain_thenReplacesHash() {
        // Arrange
        UUID user = UUID.randomUUID();
        credentials.savePasswordHash(user, "HASH-VIEJO");

        // Act
        credentials.savePasswordHash(user, "HASH-NUEVO");

        // Assert
        assertThat(credentials.findPasswordHash(user)).contains("HASH-NUEVO");
        assertThat(credentials.findPasswordHash(UUID.randomUUID())).isEmpty();
    }

    // ---------------------------------------------------------------- bitácora

    @Test
    @DisplayName("activity log: guarda una fila por evento")
    void givenEvents_whenRecord_thenOneRowPerEvent() {
        // Arrange
        long before = activityRows.count();
        UUID user = UUID.randomUUID();

        // Act
        activity.record(user, "LOGIN", Map.of(), "1.1.1.1", NOW);
        activity.record(user, "LOGOUT", Map.of("k", "v"), "1.1.1.1", NOW);
        activity.record(user, "OTRO", null, "1.1.1.1", NOW);

        // Assert
        assertThat(activityRows.count()).isEqualTo(before + 3);
    }

    @Test
    @DisplayName("activity log: toJson ordena las claves y escapa comillas, barras y saltos de línea")
    void givenSpecialCharacters_whenToJson_thenEscapesAndSortsKeys() {
        // Arrange
        Map<String, String> values = Map.of("b", "dice \"hola\"", "a", "ruta\\x\nfin");

        // Act
        String json = JpaActivityLogAdapter.toJson(values);

        // Assert
        assertThat(json).isEqualTo("{\"a\":\"ruta\\\\x\\nfin\",\"b\":\"dice \\\"hola\\\"\"}");
    }

    @Test
    @DisplayName("activity log: toJson escapa caracteres de control y trata null como vacío")
    void givenControlCharactersAndNull_whenToJson_thenEscapesThem() {
        // Arrange
        Map<String, String> values = new java.util.HashMap<>();
        values.put("t", "a\tb\rc\u0001");
        values.put("n", null);

        // Act
        String json = JpaActivityLogAdapter.toJson(values);

        // Assert
        assertThat(json).isEqualTo("{\"n\":\"\",\"t\":\"a\\tb\\rc\\u0001\"}");
    }

    // ------------------------------------------------------ códigos de un solo uso

    @ParameterizedTest(name = "{0}")
    @EnumSource(Purpose.class)
    @DisplayName("one-time codes: emitir, buscar, fallar, consumir (una sola vez)")
    void givenIssuedCode_whenFullLifecycle_thenBehavesAsOneTimeCode(Purpose purpose) {
        // Arrange
        UUID user = UUID.randomUUID();
        codes.issue(purpose, user, "HASH-1", NOW.plusSeconds(600), NOW);

        // Act
        var active = codes.findActive(purpose, user, NOW.plusSeconds(1));
        int failures = codes.registerFailedAttempt(purpose, active.orElseThrow().id());
        boolean first = codes.consume(purpose, active.get().id(), NOW.plusSeconds(2));
        boolean second = codes.consume(purpose, active.get().id(), NOW.plusSeconds(3));

        // Assert
        assertThat(active.get().codeHash()).isEqualTo("HASH-1");
        assertThat(failures).isEqualTo(1);
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(codes.findActive(purpose, user, NOW.plusSeconds(4))).isEmpty();
        assertThat(codes.lastIssuedAt(purpose, user)).contains(NOW);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Purpose.class)
    @DisplayName("one-time codes: un código nuevo invalida el anterior")
    void givenActiveCode_whenIssueAnother_thenOnlyTheNewOneIsActive(Purpose purpose) {
        // Arrange
        UUID user = UUID.randomUUID();
        codes.issue(purpose, user, "VIEJO", NOW.plusSeconds(600), NOW);

        // Act
        codes.issue(purpose, user, "NUEVO", NOW.plusSeconds(700), NOW.plusSeconds(10));

        // Assert
        assertThat(codes.findActive(purpose, user, NOW.plusSeconds(11)))
                .get().extracting(c -> c.codeHash()).isEqualTo("NUEVO");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Purpose.class)
    @DisplayName("one-time codes: un código vencido ya no está activo y sin emitir no hay historial")
    void givenExpiredCode_whenFindActive_thenEmpty(Purpose purpose) {
        // Arrange
        UUID user = UUID.randomUUID();
        codes.issue(purpose, user, "H", NOW.plusSeconds(60), NOW);

        // Act + Assert
        assertThat(codes.findActive(purpose, user, NOW.plusSeconds(61))).isEmpty();
        assertThat(codes.lastIssuedAt(purpose, UUID.randomUUID())).isEmpty();
    }
}
