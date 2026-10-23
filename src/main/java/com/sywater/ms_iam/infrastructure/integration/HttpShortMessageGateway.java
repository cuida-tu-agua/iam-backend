package com.sywater.ms_iam.infrastructure.integration;

import com.sywater.ms_iam.application.port.out.ShortMessageGateway;
import com.sywater.ms_iam.domain.exception.ExternalServiceUnavailableException;
import com.sywater.ms_iam.domain.exception.PhoneNotReachableException;
import com.sywater.ms_iam.domain.model.PhoneChannel;
import com.sywater.ms_iam.domain.model.PhoneNumber;
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

/**
 * HU-028: ms-notification "POST /internal/short-messages/verification-code" with X-Internal-Key. It sends the code at
 * once through Twilio (Mailpit in development). 202 = sent · 422 = the provider refused the number for that mode ·
 * anything else, or no answer = not available now. Without "iam.integration.notifications-url" phones cannot be
 * verified (503), so nothing pretends to work.
 */
@Component
public class HttpShortMessageGateway implements ShortMessageGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpShortMessageGateway.class);
    private static final String SERVICE = "notification";

    private final String baseUrl;
    private final String internalKey;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public HttpShortMessageGateway(@Value("${iam.integration.notifications-url:}") String baseUrl,
                                   @Value("${iam.internal.api-key:}") String internalKey) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.internalKey = internalKey;
    }

    @Override
    public void sendVerificationCode(PhoneNumber to, PhoneChannel channel, String code, Duration validFor) {
        if (baseUrl.isEmpty()) {
            log.warn("A phone code was requested but ms-notification is not configured (iam.integration.notifications-url).");
            throw new ExternalServiceUnavailableException(SERVICE);
        }
        String body = "{\"to\":\"" + to.value() + "\",\"channel\":\"" + channel.name() + "\",\"code\":\"" + code
                + "\",\"validMinutes\":" + Math.max(1, validFor.toMinutes()) + "}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/internal/short-messages/verification-code"))
                .timeout(Duration.ofSeconds(15))
                .header("X-Internal-Key", internalKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status / 100 == 2) return;
            if (status == 422) {
                log.info("The provider refused {} by {}: {}", to.masked(), channel, response.body());
                throw new PhoneNotReachableException(channel.name());
            }
            log.error("ms-notification answered {} when sending a phone code to {}: {}", status, to.masked(), response.body());
            throw new ExternalServiceUnavailableException(SERVICE);
        } catch (IOException e) {
            log.error("ms-notification could not be reached to send a phone code: {}", e.toString());
            throw new ExternalServiceUnavailableException(SERVICE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceUnavailableException(SERVICE);
        }
    }
}
