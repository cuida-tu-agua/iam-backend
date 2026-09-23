package com.sywater.ms_iam.infrastructure.config;

import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    private final IamProperties iamProperties;
    private final ResourceLoader resourceLoader;

    public JwtConfig(IamProperties iamProperties, ResourceLoader resourceLoader) {
        this.iamProperties = iamProperties;
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public RSAPublicKey rsaPublicKey() throws Exception {
        String publicKeyPem = loadKeyFile(iamProperties.jwt().publicKey());
        String publicKeyDer = extractBase64(publicKeyPem);
        byte[] decodedKey = Base64.getDecoder().decode(publicKeyDer);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(spec);
    }

    @Bean
    public RSAPrivateKey rsaPrivateKey() throws Exception {
        String privateKeyPem = loadKeyFile(iamProperties.jwt().privateKey());
        String privateKeyDer = extractBase64(privateKeyPem);
        byte[] decodedKey = Base64.getDecoder().decode(privateKeyDer);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(spec);
    }

    private String loadKeyFile(String path) throws Exception {
        var resource = resourceLoader.getResource(path);
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes());
        }
    }

    private String extractBase64(String pem) {
        return pem
                .replaceAll("-----BEGIN.*-----", "")
                .replaceAll("-----END.*-----", "")
                .replaceAll("\\s", "");
    }
}