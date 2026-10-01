package com.sywater.ms_iam.infrastructure.persistence.jpa;

import com.sywater.ms_iam.infrastructure.persistence.entity.EmailVerificationJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SpringEmailVerificationJpaRepository extends JpaRepository<EmailVerificationJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerificationJpaEntity> findFirstByUserIdAndVerifiedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(UUID userId, Instant now);

    Optional<EmailVerificationJpaEntity> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailVerificationJpaEntity c set c.expiresAt = :now where c.userId = :userId and c.verifiedAt is null and c.expiresAt > :now")
    int expireActive(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailVerificationJpaEntity c set c.failedAttempts = c.failedAttempts + 1 where c.id = :id")
    int incrementFailedAttempts(@Param("id") Long id);

    @Query("select c.failedAttempts from EmailVerificationJpaEntity c where c.id = :id")
    int findFailedAttempts(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailVerificationJpaEntity c set c.verifiedAt = :now where c.id = :id and c.verifiedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);
}
