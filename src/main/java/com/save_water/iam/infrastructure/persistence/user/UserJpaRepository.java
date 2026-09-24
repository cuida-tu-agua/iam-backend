package com.save_water.iam.infrastructure.persistence.user;

// infrastructure/persistence/UserJpaRepository.java

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
}