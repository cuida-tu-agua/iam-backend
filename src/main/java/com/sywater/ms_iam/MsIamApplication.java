package com.sywater.ms_iam;

import com.sywater.ms_iam.infrastructure.config.IamProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(IamProperties.class)
public class MsIamApplication {
    public static void main(String[] args) {
        SpringApplication.run(MsIamApplication.class, args);
    }
}