package com.sywater.ms_iam.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface ActivityLog {

    void record(UUID userId, String action, Map<String, String> metadata, String ip, Instant at);
}
