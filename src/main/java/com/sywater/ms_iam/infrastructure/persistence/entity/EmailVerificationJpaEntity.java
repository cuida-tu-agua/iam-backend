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
@Table(name = "email_verifications", schema = "security")
public class EmailVerificationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 256)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EmailVerificationJpaEntity() {
    }

    public static EmailVerificationJpaEntity create(UUID userId, String tokenHash, Instant expiresAt, Instant now) {
        EmailVerificationJpaEntity e = new EmailVerificationJpaEntity();
        e.userId = userId;
        e.tokenHash = tokenHash;
        e.expiresAt = expiresAt;
        e.createdAt = now;
        return e;
    }

    public Long getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getCreatedAt() { return createdAt; }
}
