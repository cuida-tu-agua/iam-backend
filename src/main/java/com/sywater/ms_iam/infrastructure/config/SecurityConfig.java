package com.sywater.ms_iam.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // CSRF: Deshabilitado solo para endpoints públicos (/api/auth/*)
                // Mantener habilitado para otros endpoints
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/auth/**")
                )

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth
                        // Permitir acceso público a endpoints de auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // Permitir acceso a health checks
                        .requestMatchers("/actuator/health").permitAll()
                        // Proteger otros actuator endpoints
                        .requestMatchers("/actuator/**").authenticated()
                        // Todo lo demás requiere autenticación
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
