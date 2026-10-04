package com.sywater.ms_iam.infrastructure.persistence.adapter;

import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.EmailAlreadyRegisteredException;
import com.sywater.ms_iam.domain.exception.PhoneAlreadyRegisteredException;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.PhoneNumber;
import com.sywater.ms_iam.domain.model.Role;
import com.sywater.ms_iam.domain.model.User;
import com.sywater.ms_iam.infrastructure.persistence.entity.RoleJpaEntity;
import com.sywater.ms_iam.infrastructure.persistence.entity.UserJpaEntity;
import com.sywater.ms_iam.infrastructure.persistence.entity.UserRoleJpaEntity;
import com.sywater.ms_iam.infrastructure.persistence.jpa.SpringRoleJpaRepository;
import com.sywater.ms_iam.infrastructure.persistence.jpa.SpringUserJpaRepository;
import com.sywater.ms_iam.infrastructure.persistence.jpa.SpringUserRoleJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JpaUserRepositoryAdapter implements UserRepository {

    private final SpringUserJpaRepository users;
    private final SpringRoleJpaRepository roles;
    private final SpringUserRoleJpaRepository userRoles;
    private final EntityManager entityManager;

    public JpaUserRepositoryAdapter(SpringUserJpaRepository users, SpringRoleJpaRepository roles,
                                    SpringUserRoleJpaRepository userRoles, EntityManager entityManager) {
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return users.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return users.findByEmail(email.value()).map(this::toDomain);
    }

    @Override
    public Optional<User> findByPhone(PhoneNumber phone) {
        return users.findByPhone(phone.value()).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return users.existsByEmail(email.value());
    }

    @Override
    public boolean phoneTakenByOther(PhoneNumber phone, UUID exceptUserId) {
        return users.phoneTakenByOther(phone.value(), exceptUserId);
    }

    @Override
    public void create(User user) {
        UserJpaEntity entity = new UserJpaEntity(user.id());
        UserMapper.copy(user, entity);
        try {
            entityManager.persist(entity);
            entityManager.flush();   // hits UQ_users_email now, not at commit time
        } catch (DataIntegrityViolationException | PersistenceException e) {
            String message = String.valueOf(e.getMessage()) + (e.getCause() == null ? "" : e.getCause().getMessage());
            if (message.contains("UQ_users_email")) throw new EmailAlreadyRegisteredException();
            if (message.contains("UX_users_phone")) throw new PhoneAlreadyRegisteredException();
            throw e;
        }

        for (Role role : user.roles()) {
            RoleJpaEntity roleEntity = roles.findByCode(role.name()).orElseThrow(() ->
                    new IllegalStateException("Role " + role + " is missing in security.roles. Run ms-iam-db with contexts=seed."));
            userRoles.save(new UserRoleJpaEntity(user.id(), roleEntity.getId(), user.createdAt()));
        }
    }

    @Override
    public void update(User user) {
        UserJpaEntity entity = users.findById(user.id())
                .orElseThrow(() -> new IllegalStateException("User " + user.id() + " does not exist."));
        UserMapper.copy(user, entity);
        users.save(entity);
    }

    private User toDomain(UserJpaEntity entity) {
        return UserMapper.toDomain(entity, users.findRoleCodes(entity.getId()));
    }
}
