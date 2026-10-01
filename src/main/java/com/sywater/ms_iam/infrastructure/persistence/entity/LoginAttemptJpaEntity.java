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
@Table(name = "login_attempts", schema = "security")
public class LoginAttemptJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "failure_reason", length = 50)
    private String failureReason;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected LoginAttemptJpaEntity() {
    }

    public LoginAttemptJpaEntity(UUID userId, String email, String ipAddress, boolean success, String failureReason,
                                 Instant attemptedAt) {
        this.userId = userId;
        this.email = email;
        this.ipAddress = ipAddress;
        this.success = success;
        this.failureReason = failureReason;
        this.attemptedAt = attemptedAt;
    }

    public Long getId() { return id; }
    public UUID getUserId() { return userId; }
    public boolean isSuccess() { return success; }
    public Instant getAttemptedAt() { return attemptedAt; }
}
