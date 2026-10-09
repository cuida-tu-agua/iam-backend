package com.sywater.ms_iam.infrastructure.security;

import com.sywater.ms_iam.domain.exception.InternalOnlyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La llave entre servicios (X-Internal-Key) falla cerrada: sin llave válida configurada, nada pasa. */
class InternalKeyGuardTest {

    private static final String KEY = "clave-interna-de-prueba-123456";

    @Test
    @DisplayName("require: la llave correcta pasa")
    void givenConfiguredKey_whenRequireWithSameKey_thenPasses() {
        // Arrange
        InternalKeyGuard guard = new InternalKeyGuard(KEY);

        // Act + Assert
        assertThatCode(() -> guard.require(KEY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("require: una llave distinta o ausente se rechaza")
    void givenConfiguredKey_whenRequireWithWrongOrNull_thenThrows() {
        // Arrange
        InternalKeyGuard guard = new InternalKeyGuard(KEY);

        // Act + Assert
        assertThatThrownBy(() -> guard.require("otra-llave")).isInstanceOf(InternalOnlyException.class);
        assertThatThrownBy(() -> guard.require(null)).isInstanceOf(InternalOnlyException.class);
    }

    @Test
    @DisplayName("require: sin llave configurada o demasiado corta, todo se rechaza")
    void givenMissingOrShortKey_whenRequire_thenAlwaysThrows() {
        // Act + Assert
        assertThatThrownBy(() -> new InternalKeyGuard("").require("")).isInstanceOf(InternalOnlyException.class);
        assertThatThrownBy(() -> new InternalKeyGuard(null).require("x")).isInstanceOf(InternalOnlyException.class);
        assertThatThrownBy(() -> new InternalKeyGuard("corta").require("corta")).isInstanceOf(InternalOnlyException.class);
    }
}
