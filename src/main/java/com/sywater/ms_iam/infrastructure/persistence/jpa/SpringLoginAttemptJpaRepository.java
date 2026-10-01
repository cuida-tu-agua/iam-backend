package com.sywater.ms_iam.infrastructure.persistence.jpa;

import com.sywater.ms_iam.infrastructure.persistence.entity.LoginAttemptJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface SpringLoginAttemptJpaRepository extends JpaRepository<LoginAttemptJpaEntity, Long> {

    @Query("select count(a) from LoginAttemptJpaEntity a where a.userId = :userId and a.success = false " +
            "and a.failureReason = 'WRONG_PASSWORD' and a.attemptedAt > :since")
    long countWrongPasswordsSince(@Param("userId") UUID userId, @Param("since") Instant since);   // IX_login_user_time

    @Query("select max(a.attemptedAt) from LoginAttemptJpaEntity a where a.userId = :userId and a.success = true")
    Instant findLastSuccessAt(@Param("userId") UUID userId);
}
