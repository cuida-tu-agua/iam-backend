package com.sywater.ms_iam.infrastructure.persistence.jpa;

import com.sywater.ms_iam.infrastructure.persistence.entity.PasswordResetJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SpringPasswordResetJpaRepository extends JpaRepository<PasswordResetJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetJpaEntity> findFirstByUserIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(UUID userId, Instant now);

    Optional<PasswordResetJpaEntity> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetJpaEntity c set c.expiresAt = :now where c.userId = :userId and c.usedAt is null and c.expiresAt > :now")
    int expireActive(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetJpaEntity c set c.failedAttempts = c.failedAttempts + 1 where c.id = :id")
    int incrementFailedAttempts(@Param("id") Long id);

    @Query("select c.failedAttempts from PasswordResetJpaEntity c where c.id = :id")
    int findFailedAttempts(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetJpaEntity c set c.usedAt = :now where c.id = :id and c.usedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);
}
