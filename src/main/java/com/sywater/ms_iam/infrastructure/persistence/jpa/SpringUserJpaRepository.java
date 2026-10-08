package com.sywater.ms_iam.infrastructure.persistence.jpa;

import com.sywater.ms_iam.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /** Roles of many users in ONE query (the admin list would otherwise ask once per row). Rows: [userId, roleCode]. */
    @Query("select ur.userId, r.code from UserRoleJpaEntity ur, RoleJpaEntity r where ur.roleId = r.id and ur.userId in :ids")
    List<Object[]> findRoleCodesOf(@Param("ids") Collection<UUID> ids);

    /**
     * HU-059. status: ALL | ACTIVE | BLOCKED | UNVERIFIED. pattern: already lower-case, with % around and the
     * wildcards of the user escaped with '!'. A deleted account never matches.
     */
    @Query("""
            select u from UserJpaEntity u
            where u.deletedAt is null
              and (:status = 'ALL'
                   or (:status = 'BLOCKED' and u.blockedAt is not null)
                   or (:status = 'ACTIVE' and u.blockedAt is null and u.emailVerified = true)
                   or (:status = 'UNVERIFIED' and u.blockedAt is null and u.emailVerified = false))
              and (lower(u.email) like :pattern escape '!'
                   or lower(u.firstName) like :pattern escape '!'
                   or lower(u.lastName) like :pattern escape '!'
                   or lower(concat(u.firstName, ' ', u.lastName)) like :pattern escape '!')
            order by u.createdAt desc, u.id
            """)
    Page<UserJpaEntity> search(@Param("status") String status, @Param("pattern") String pattern, Pageable pageable);
}
