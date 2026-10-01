package com.sywater.ms_iam.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_credentials", schema = "security")
public class UserCredentialJpaEntity {

    public static final String LOCAL = "LOCAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "credential_type", nullable = false, length = 10)
    private String credentialType;

    @Column(name = "password_hash", length = 256)
    private String passwordHash;

    @Column(name = "google_id", length = 256)
    private String googleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserCredentialJpaEntity() {
    }

    public static UserCredentialJpaEntity local(UUID userId, String passwordHash, Instant now) {
        UserCredentialJpaEntity e = new UserCredentialJpaEntity();
        e.userId = userId;
        e.credentialType = LOCAL;
        e.passwordHash = passwordHash;
        e.createdAt = now;
        return e;
    }

    public Long getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getCredentialType() { return credentialType; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getGoogleId() { return googleId; }
    public Instant getCreatedAt() { return createdAt; }
}
