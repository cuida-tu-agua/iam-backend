package com.sywater.ms_iam.infrastructure.persistence.jpa;

import com.sywater.ms_iam.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringUserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmail(String email);                 // UQ_users_email

    Optional<UserJpaEntity> findByPhone(String phone);                 // UX_users_phone

    boolean existsByEmail(String email);

    @Query("select count(u) > 0 from UserJpaEntity u where u.phone = :phone and (:exceptId is null or u.id <> :exceptId)")
    boolean phoneTakenByOther(@Param("phone") String phone, @Param("exceptId") UUID exceptId);

    @Query("select r.code from UserRoleJpaEntity ur, RoleJpaEntity r where ur.roleId = r.id and ur.userId = :userId")
    List<String> findRoleCodes(@Param("userId") UUID userId);
}
