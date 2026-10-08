package com.sywater.ms_iam.infrastructure.integration;

import com.sywater.ms_iam.application.port.out.DeviceCleanup;
import com.sywater.ms_iam.domain.exception.ExternalServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * HU-008: when an account is deleted, ms-device releases every device that user linked
 * (DELETE /internal/users/{id}/devices, authenticated with X-Internal-Key).
 * Without "iam.integration.devices-url" it only leaves a trace, so IAM still runs alone in development.
 * If ms-device is configured but does not answer, the deletion fails (503) and nothing is changed:
 * the user can retry, and no device is left linked to a deleted account.
 */
@Component
public class HttpDeviceCleanup implements DeviceCleanup {

    private static final Logger log = LoggerFactory.getLogger(HttpDeviceCleanup.class);

    private final String baseUrl;
    private final String internalKey;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public HttpDeviceCleanup(@Value("${iam.integration.devices-url:}") String baseUrl,
                             @Value("${iam.internal.api-key:}") String internalKey) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.internalKey = internalKey;
    }

    @Override
    public void unlinkAllDevicesOf(UUID userId) {
        if (baseUrl.isEmpty()) {
            log.info("Account {} deleted: ms-device is not configured (iam.integration.devices-url), devices were not unlinked.", userId);
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/internal/users/" + userId + "/devices"))
                .timeout(Duration.ofSeconds(5))
                .header("X-Internal-Key", internalKey)
                .DELETE()
                .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                log.error("ms-device answered {} when unlinking the devices of {}.", response.statusCode(), userId);
                throw new ExternalServiceUnavailableException("device");
            }
        } catch (IOException e) {
            log.error("ms-device could not be reached to unlink the devices of {}: {}", userId, e.toString());
            throw new ExternalServiceUnavailableException("device");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceUnavailableException("device");
        }
    }
}
