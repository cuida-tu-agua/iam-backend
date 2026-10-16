package com.sywater.ms_iam.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    /** The document points to the gateway prefix (/iam) so "Try it out" works from Swagger UI. */
    @Bean
    OpenAPI iamOpenApi(@Value("${openapi.server-url:/}") String serverUrl) {
        return new OpenAPI()
                .info(new Info().title("ms-iam").version("1.0.0").description("Accounts, sessions and administration"))
                .servers(List.of(new Server().url(serverUrl)));
    }
}
