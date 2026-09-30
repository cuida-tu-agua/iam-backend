package com.sywater.ms_iam.application.port.out;

import java.util.UUID;

public interface AvatarStorage {

    String store(UUID userId, byte[] content, String extension);

    void delete(String url);
}
