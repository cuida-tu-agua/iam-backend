package com.save_water.iam.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.save_water.iam.domain.model.user.User;
import com.save_water.iam.domain.port.out.loginCase.TokenGeneratorPort;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// infrastructure/security/JwtTokenGeneratorAdapter.java
@Component
public class JwtTokenGeneratorAdapter implements TokenGeneratorPort {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expirationMs;

    @Override
    public String generate(User user) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId().value().toString())
                .claim("roles", user.getRoles().stream().map(role -> role.name()).collect(Collectors.toSet()))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }
}