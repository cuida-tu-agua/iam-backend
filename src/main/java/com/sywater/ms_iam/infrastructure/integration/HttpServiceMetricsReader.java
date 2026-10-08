package com.sywater.ms_iam.infrastructure.integration;

import com.sywater.ms_iam.application.dto.ServiceTotals;
import com.sywater.ms_iam.application.port.out.ServiceMetricsReader;
import com.sywater.ms_iam.domain.exception.ExternalServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HU-062: GET /internal/metrics of ms-places and ms-device, authenticated with X-Internal-Key.
 * Any failure (not configured, down, error answer, unreadable body) is "service unavailable" for that section only.
 */
@Component
public class HttpServiceMetricsReader implements ServiceMetricsReader {

    private static final Logger log = LoggerFactory.getLogger(HttpServiceMetricsReader.class);

    private final String placesUrl;
    private final String devicesUrl;
    private final String internalKey;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public HttpServiceMetricsReader(@Value("${iam.integration.places-url:}") String placesUrl,
                                    @Value("${iam.integration.devices-url:}") String devicesUrl,
                                    @Value("${iam.internal.api-key:}") String internalKey) {
        this.placesUrl = clean(placesUrl);
        this.devicesUrl = clean(devicesUrl);
        this.internalKey = internalKey;
    }

    @Override
    public ServiceTotals.Places places() {
        JsonNode body = get(placesUrl, "places");
        return new ServiceTotals.Places(number(body, "total", "places"));
    }

    @Override
    public ServiceTotals.Devices devices() {
        JsonNode body = get(devicesUrl, "device");
        return new ServiceTotals.Devices(number(body, "total", "device"), number(body, "connected", "device"),
                number(body, "linked", "device"));
    }

    private JsonNode get(String baseUrl, String service) {
        if (baseUrl.isEmpty()) throw new ExternalServiceUnavailableException(service);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/internal/metrics"))
                .timeout(Duration.ofSeconds(4))
                .header("X-Internal-Key", internalKey)
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("The {} service answered {} to /internal/metrics.", service, response.statusCode());
                throw new ExternalServiceUnavailableException(service);
            }
            return json.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceUnavailableException(service);
        } catch (ExternalServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("The {} service could not be read for the metrics: {}", service, e.toString());
            throw new ExternalServiceUnavailableException(service);
        }
    }

    private static long number(JsonNode body, String field, String service) {
        JsonNode value = body.get(field);
        if (value == null || !value.isNumber()) {
            log.warn("The {} service answered the metrics without a numeric '{}'.", service, field);
            throw new ExternalServiceUnavailableException(service);
        }
        return value.asLong();
    }

    private static String clean(String url) {
        return url == null ? "" : url.trim().replaceAll("/+$", "");
    }
}
