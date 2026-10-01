package com.sywater.ms_iam.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class KeysConfig {

    private final IamProperties properties;
    private final ResourceLoader resources;

    public KeysConfig(IamProperties properties, ResourceLoader resources) {
        this.properties = properties;
        this.resources = resources;
    }

    @Bean
    public RSAPublicKey rsaPublicKey() throws Exception {
        byte[] der = readPem(properties.jwt().publicKey());
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    @Bean
    public RSAPrivateKey rsaPrivateKey() throws Exception {
        byte[] der = readPem(properties.jwt().privateKey());
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private byte[] readPem(String location) throws Exception {
        try (InputStream in = resources.getResource(location).getInputStream()) {
            String pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
            String base64 = pem.replaceAll("-----(BEGIN|END)[^-]+-----", "").replaceAll("\\s", "");
            return Base64.getDecoder().decode(base64);
        }
    }
}
