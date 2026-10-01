package com.sywater.ms_iam.infrastructure.persistence.jpa;

import com.sywater.ms_iam.infrastructure.persistence.entity.ActivityLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringActivityLogJpaRepository extends JpaRepository<ActivityLogJpaEntity, Long> {
}
