package com.sywater.ms_iam.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración de Spring MVC.
 * El rate limiting se maneja mediante RateLimitingFilter (@Component).
 * Este archivo está disponible para configuraciones adicionales de MVC si es necesario.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    // RateLimitingFilter se registra automáticamente como @Component
    // No necesita configuración adicional aquí
}
