package com.sywater.ms_iam.infrastructure.storage;

import com.sywater.ms_iam.infrastructure.config.IamProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-007: la foto de perfil se guarda en disco; nunca se borra nada fuera de su carpeta. */
class LocalAvatarStorageTest {

    @TempDir Path tmp;

    private Path avatars;
    private LocalAvatarStorage storage;

    @BeforeEach
    void setUp() {
        avatars = tmp.resolve("avatars");
        IamProperties properties = new IamProperties(null, null, null,
                new IamProperties.Avatars(avatars.toString(), 2_000_000L), null, null);
        storage = new LocalAvatarStorage(properties);
    }

    @Test
    @DisplayName("store: crea la carpeta, escribe el archivo y devuelve su URL pública")
    void givenContent_whenStore_thenWritesFileAndReturnsUrl() throws IOException {
        // Arrange
        byte[] content = {1, 2, 3};

        // Act
        String url = storage.store(UUID.randomUUID(), content, "png");

        // Assert
        assertThat(url).startsWith(LocalAvatarStorage.URL_PREFIX).endsWith(".png");
        Path file = avatars.resolve(url.substring(LocalAvatarStorage.URL_PREFIX.length()));
        assertThat(Files.readAllBytes(file)).containsExactly(content);
    }

    @Test
    @DisplayName("delete: borra la foto guardada")
    void givenStoredAvatar_whenDelete_thenFileIsRemoved() {
        // Arrange
        String url = storage.store(UUID.randomUUID(), new byte[]{9}, "jpg");
        Path file = avatars.resolve(url.substring(LocalAvatarStorage.URL_PREFIX.length()));

        // Act
        storage.delete(url);

        // Assert
        assertThat(file).doesNotExist();
    }

    @Test
    @DisplayName("delete: ignora URLs nulas, ajenas o de un archivo que ya no existe")
    void givenNullForeignOrMissingUrl_whenDelete_thenDoesNothing() {
        // Act + Assert (no debe lanzar excepción)
        storage.delete(null);
        storage.delete("https://otro-sitio.com/foto.png");
        storage.delete(LocalAvatarStorage.URL_PREFIX + "no-existe.png");
    }

    @Test
    @DisplayName("delete: una ruta con ../ no puede borrar archivos fuera de la carpeta")
    void givenPathTraversal_whenDelete_thenOutsideFileSurvives() throws IOException {
        // Arrange
        Path secret = tmp.resolve("secreto.txt");
        Files.writeString(secret, "no me borres");

        // Act
        storage.delete(LocalAvatarStorage.URL_PREFIX + "../secreto.txt");

        // Assert
        assertThat(secret).exists();
    }
}
