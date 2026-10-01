package com.sywater.ms_iam.infrastructure.storage;

import com.sywater.ms_iam.application.port.out.AvatarStorage;
import com.sywater.ms_iam.infrastructure.config.IamProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class LocalAvatarStorage implements AvatarStorage {

    public static final String URL_PREFIX = "/api/avatars/";
    private static final Logger log = LoggerFactory.getLogger(LocalAvatarStorage.class);

    private final Path directory;

    public LocalAvatarStorage(IamProperties properties) {
        this.directory = Path.of(properties.avatars().storageDir()).toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID userId, byte[] content, String extension) {
        String fileName = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(fileName), content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the photo", e);
        }
        return URL_PREFIX + fileName;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) return;
        Path file = directory.resolve(url.substring(URL_PREFIX.length())).normalize();
        if (!file.startsWith(directory)) return;   // "../" in the name: never delete outside the folder
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete old avatar {}: {}", file, e.getMessage());
        }
    }
}
