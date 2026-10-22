package com.sywater.ms_iam.infrastructure;

import com.sun.net.httpserver.HttpServer;
import com.sywater.ms_iam.domain.exception.ExternalServiceUnavailableException;
import com.sywater.ms_iam.domain.exception.PhoneNotReachableException;
import com.sywater.ms_iam.domain.model.PhoneChannel;
import com.sywater.ms_iam.domain.model.PhoneNumber;
import com.sywater.ms_iam.infrastructure.integration.HttpShortMessageGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-028: the call IAM makes to ms-notification to deliver the phone code. */
class HttpShortMessageGatewayTest {

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> key = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private String start(int status) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            key.set(exchange.getRequestHeaders().getFirst("X-Internal-Key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] answer = "{\"title\":\"x\"}".getBytes();
            exchange.sendResponseHeaders(status, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void sends_the_number_mode_code_and_minutes_with_the_shared_key() throws Exception {
        new HttpShortMessageGateway(start(202) + "/", "secreta")
                .sendVerificationCode(new PhoneNumber("+573001112233"), PhoneChannel.WHATSAPP, "123456", Duration.ofMinutes(5));

        assertThat(path.get()).isEqualTo("/internal/short-messages/verification-code");
        assertThat(key.get()).isEqualTo("secreta");
        assertThat(body.get()).isEqualTo("{\"to\":\"+573001112233\",\"channel\":\"WHATSAPP\",\"code\":\"123456\",\"validMinutes\":5}");
    }

    @Test
    void a_refused_number_is_not_reachable() throws Exception {
        HttpShortMessageGateway gateway = new HttpShortMessageGateway(start(422), "secreta");

        assertThatThrownBy(() -> gateway.sendVerificationCode(new PhoneNumber("+573001112233"), PhoneChannel.WHATSAPP, "123456", Duration.ofMinutes(5)))
                .isInstanceOf(PhoneNotReachableException.class);
    }

    @Test
    void any_other_answer_or_no_service_is_unavailable() throws Exception {
        HttpShortMessageGateway down = new HttpShortMessageGateway(start(503), "secreta");
        assertThatThrownBy(() -> down.sendVerificationCode(new PhoneNumber("+573001112233"), PhoneChannel.SMS, "123456", Duration.ofMinutes(5)))
                .isInstanceOf(ExternalServiceUnavailableException.class);

        HttpShortMessageGateway notConfigured = new HttpShortMessageGateway("", "secreta");
        assertThatThrownBy(() -> notConfigured.sendVerificationCode(new PhoneNumber("+573001112233"), PhoneChannel.SMS, "123456", Duration.ofMinutes(5)))
                .isInstanceOf(ExternalServiceUnavailableException.class);

        HttpShortMessageGateway nobody = new HttpShortMessageGateway("http://127.0.0.1:1", "secreta");
        assertThatThrownBy(() -> nobody.sendVerificationCode(new PhoneNumber("+573001112233"), PhoneChannel.SMS, "123456", Duration.ofMinutes(5)))
                .isInstanceOf(ExternalServiceUnavailableException.class);
    }
}
