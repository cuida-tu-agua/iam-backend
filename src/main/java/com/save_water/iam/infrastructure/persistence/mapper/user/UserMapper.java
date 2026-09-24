package com.save_water.iam.infrastructure.persistence.mapper.user;

// infrastructure/persistence/UserMapper.java

import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;

import com.save_water.iam.domain.model.user.Role;
import com.save_water.iam.domain.model.user.User;
import com.save_water.iam.domain.model.user.UserId;
import com.save_water.iam.infrastructure.persistence.user.UserEntity;

@Mapper(componentModel = "spring")
public interface UserMapper {

    default User toDomain(UserEntity entity) {
        Set<Role> roles = entity.getRoles().stream()
                .map(roleEntity -> new Role(roleEntity.getName()))
                .collect(Collectors.toSet());

        return new User(
                new UserId(entity.getId()),
                entity.getEmail(),
                entity.getPasswordHash(),
                roles
        );
    }

    default UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.getId().value());
        entity.setEmail(user.getEmail());
        entity.setPasswordHash(user.getPasswordHash());
        // roles se asignan aparte (ver nota abajo)
        return entity;
    }
}