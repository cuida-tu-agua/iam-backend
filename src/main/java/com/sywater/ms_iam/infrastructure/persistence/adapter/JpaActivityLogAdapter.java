package com.sywater.ms_iam.infrastructure.persistence.adapter;

import com.sywater.ms_iam.application.port.out.ActivityLog;
import com.sywater.ms_iam.infrastructure.persistence.entity.ActivityLogJpaEntity;
import com.sywater.ms_iam.infrastructure.persistence.jpa.SpringActivityLogJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Component
public class JpaActivityLogAdapter implements ActivityLog {

    private final SpringActivityLogJpaRepository log;

    public JpaActivityLogAdapter(SpringActivityLogJpaRepository log) {
        this.log = log;
    }

    @Override
    public void record(UUID userId, String action, Map<String, String> metadata, String ip, Instant at) {
        String json = metadata == null || metadata.isEmpty() ? null : toJson(metadata);
        log.save(new ActivityLogJpaEntity(userId, action, json, ip, at));
    }

    static String toJson(Map<String, String> values) {
        StringBuilder sb = new StringBuilder("{");
        new TreeMap<>(values).forEach((k, v) -> {
            if (sb.length() > 1) sb.append(',');
            sb.append('"').append(escape(k)).append("\":\"").append(escape(v)).append('"');
        });
        return sb.append('}').toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
