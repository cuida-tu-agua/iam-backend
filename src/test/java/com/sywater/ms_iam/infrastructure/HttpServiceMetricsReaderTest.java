package com.sywater.ms_iam.infrastructure;

import com.sun.net.httpserver.HttpServer;
import com.sywater.ms_iam.application.dto.ServiceTotals;
import com.sywater.ms_iam.domain.exception.ExternalServiceUnavailableException;
import com.sywater.ms_iam.infrastructure.integration.HttpServiceMetricsReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-062: how IAM reads the numbers of ms-places and ms-device. */
class HttpServiceMetricsReaderTest {

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> key = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private String start(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            key.set(exchange.getRequestHeaders().getFirst("X-Internal-Key"));
            byte[] bytes = body.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void reads_the_place_total_with_the_shared_key() throws Exception {
        HttpServiceMetricsReader reader = new HttpServiceMetricsReader(start(200, "{\"total\":7}") + "/", "", "secreta");

        assertThat(reader.places()).isEqualTo(new ServiceTotals.Places(7));
        assertThat(path.get()).isEqualTo("/internal/metrics");
        assertThat(key.get()).isEqualTo("secreta");
    }

    @Test
    void reads_the_device_numbers() throws Exception {
        String url = start(200, "{\"total\":9,\"connected\":4,\"linked\":6}");

        assertThat(new HttpServiceMetricsReader("", url, "k").devices()).isEqualTo(new ServiceTotals.Devices(9, 4, 6));
    }

    @Test
    void a_forbidden_or_broken_answer_is_service_unavailable() throws Exception {
        HttpServiceMetricsReader forbidden = new HttpServiceMetricsReader(start(403, "{}"), "", "mala");
        assertThatThrownBy(forbidden::places).isInstanceOf(ExternalServiceUnavailableException.class);
        stop();

        HttpServiceMetricsReader garbage = new HttpServiceMetricsReader(start(200, "no es json"), "", "k");
        assertThatThrownBy(garbage::places).isInstanceOf(ExternalServiceUnavailableException.class);
        stop();

        HttpServiceMetricsReader missing = new HttpServiceMetricsReader("", start(200, "{\"total\":1}"), "k");
        assertThatThrownBy(missing::devices).isInstanceOf(ExternalServiceUnavailableException.class);
    }

    @Test
    void a_service_that_is_down_or_not_configured_is_service_unavailable() {
        assertThatThrownBy(new HttpServiceMetricsReader("http://127.0.0.1:1", "", "k")::places)
                .isInstanceOf(ExternalServiceUnavailableException.class);
        assertThatThrownBy(new HttpServiceMetricsReader("", "", "k")::devices)
                .isInstanceOf(ExternalServiceUnavailableException.class);
    }
}
