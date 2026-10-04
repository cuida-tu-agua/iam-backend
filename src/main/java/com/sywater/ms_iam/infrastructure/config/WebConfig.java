package com.sywater.ms_iam.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final IamProperties properties;

    public WebConfig(IamProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(properties.avatars().storageDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/api/avatars/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
