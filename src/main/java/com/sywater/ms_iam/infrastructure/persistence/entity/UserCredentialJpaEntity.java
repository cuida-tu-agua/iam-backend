package com.sywater.ms_iam.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "user_credentials", schema = "security")
public class UserCredentialJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "credential_type", nullable = false)
    private String credentialType;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Constructor sin parámetros (OBLIGATORIO para Hibernate/JPA)
    public UserCredentialJpaEntity() {
    }

    // Constructor personalizado para crear credenciales
    public UserCredentialJpaEntity(UUID userId, String passwordHash) {
        this.userId = userId;
        this.credentialType = "LOCAL";
        this.passwordHash = passwordHash;
        this.createdAt = LocalDateTime.now(ZoneId.of("UTC"));
    }

    // Getters
    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCredentialType() {
        return credentialType;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // Setters
    public void setId(Long id) {
        this.id = id;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public void setCredentialType(String credentialType) {
        this.credentialType = credentialType;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
