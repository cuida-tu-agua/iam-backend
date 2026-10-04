package com.sywater.ms_iam.infrastructure.persistence.jpa;

import com.sywater.ms_iam.infrastructure.persistence.entity.UserCredentialJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringCredentialJpaRepository extends JpaRepository<UserCredentialJpaEntity, Long> {

    Optional<UserCredentialJpaEntity> findByUserIdAndCredentialType(UUID userId, String credentialType); // UQ_cred_user_type
}
