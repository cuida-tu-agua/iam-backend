package com.sywater.ms_iam.infrastructure.persistence.repository;

import com.sywater.ms_iam.infrastructure.persistence.entity.UserCredentialJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserCredentialRepository extends JpaRepository<UserCredentialJpaEntity, Long> {
    Optional<UserCredentialJpaEntity> findByUserId(UUID userId);
}