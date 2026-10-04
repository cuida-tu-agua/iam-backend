package com.sywater.ms_iam.infrastructure.persistence.jpa;

import com.sywater.ms_iam.infrastructure.persistence.entity.UserRoleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringUserRoleJpaRepository extends JpaRepository<UserRoleJpaEntity, UserRoleJpaEntity.Key> {
}
