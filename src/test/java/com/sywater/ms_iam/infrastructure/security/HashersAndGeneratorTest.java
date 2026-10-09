package com.sywater.ms_iam.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Utilidades de seguridad: hash de contraseñas (BCrypt), hash de secretos (SHA-256) y generador aleatorio. */
class HashersAndGeneratorTest {

    private final BCryptPasswordHasher passwords = new BCryptPasswordHasher();
    private final Sha256SecretHasher secrets = new Sha256SecretHasher();
    private final SecureRandomSecretGenerator generator = new SecureRandomSecretGenerator();

    @Test
    @DisplayName("BCrypt: la contraseña correcta coincide y la incorrecta no")
    void givenHashedPassword_whenMatches_thenOnlyTheRightOneMatches() {
        // Arrange
        String hash = passwords.hash("Agua2026!");

        // Act + Assert
        assertThat(hash).isNotEqualTo("Agua2026!").startsWith("$2");
        assertThat(passwords.matches("Agua2026!", hash)).isTrue();
        assertThat(passwords.matches("otra", hash)).isFalse();
    }

    @Test
    @DisplayName("BCrypt: un hash nulo nunca coincide")
    void givenNullHash_whenMatches_thenFalse() {
        // Act + Assert
        assertThat(passwords.matches("Agua2026!", null)).isFalse();
    }

    @Test
    @DisplayName("BCrypt: el mismo texto produce hashes distintos (sal aleatoria)")
    void givenSamePassword_whenHashedTwice_thenHashesDiffer() {
        // Act + Assert
        assertThat(passwords.hash("Agua2026!")).isNotEqualTo(passwords.hash("Agua2026!"));
    }

    @Test
    @DisplayName("SHA-256: es determinista, de 64 caracteres hex y compara bien")
    void givenSecret_whenHashed_thenDeterministicHex() {
        // Act
        String hash = secrets.hash("abc");

        // Assert
        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(secrets.matches("abc", hash)).isTrue();
        assertThat(secrets.matches("abd", hash)).isFalse();
        assertThat(secrets.matches("abc", null)).isFalse();
    }

    @Test
    @DisplayName("generador: el código tiene siempre 6 dígitos")
    void givenGenerator_whenSixDigitCode_thenAlwaysSixDigits() {
        // Act + Assert
        for (int i = 0; i < 200; i++) {
            assertThat(generator.sixDigitCode()).matches("\\d{6}");
        }
    }

    @Test
    @DisplayName("generador: el token opaco es URL-safe, largo y no se repite")
    void givenGenerator_whenOpaqueToken_thenUrlSafeAndUnique() {
        // Act
        String first = generator.opaqueToken();
        String second = generator.opaqueToken();

        // Assert
        assertThat(first).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second);
    }
}
