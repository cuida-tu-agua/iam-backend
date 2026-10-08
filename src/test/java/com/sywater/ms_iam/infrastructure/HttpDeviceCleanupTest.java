package com.sywater.ms_iam.infrastructure;

import com.sun.net.httpserver.HttpServer;
import com.sywater.ms_iam.domain.exception.ExternalServiceUnavailableException;
import com.sywater.ms_iam.infrastructure.integration.HttpDeviceCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-008: the call IAM makes to ms-device when an account is deleted. */
class HttpDeviceCleanupTest {

    private HttpServer server;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> key = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private String start(int status) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            key.set(exchange.getRequestHeaders().getFirst("X-Internal-Key"));
            byte[] body = "{\"unlinked\":1}".getBytes();
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void calls_the_internal_endpoint_of_ms_device_with_the_shared_key() throws Exception {
        UUID user = UUID.randomUUID();

        new HttpDeviceCleanup(start(200) + "/", "secreta").unlinkAllDevicesOf(user);

        assertThat(method.get()).isEqualTo("DELETE");
        assertThat(path.get()).isEqualTo("/internal/users/" + user + "/devices");
        assertThat(key.get()).isEqualTo("secreta");
    }

    @Test
    void an_error_answer_stops_the_deletion_with_service_unavailable() throws Exception {
        HttpDeviceCleanup cleanup = new HttpDeviceCleanup(start(403), "mala");

        assertThatThrownBy(() -> cleanup.unlinkAllDevicesOf(UUID.randomUUID()))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", "service.unavailable");
    }

    @Test
    void a_service_that_is_down_stops_the_deletion_too() {
        HttpDeviceCleanup cleanup = new HttpDeviceCleanup("http://127.0.0.1:1", "k");

        assertThatThrownBy(() -> cleanup.unlinkAllDevicesOf(UUID.randomUUID()))
                .isInstanceOf(ExternalServiceUnavailableException.class);
    }

    @Test
    void without_a_configured_url_it_only_logs_so_iam_runs_alone() {
        assertThatCode(() -> new HttpDeviceCleanup("", "k").unlinkAllDevicesOf(UUID.randomUUID())).doesNotThrowAnyException();
    }
}
