package com.save_water.iam.infrastructure.persistence.login;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.save_water.iam.domain.model.user.User;
import com.save_water.iam.domain.port.out.loginCase.UserRepositoryPort;
import com.save_water.iam.infrastructure.persistence.mapper.user.UserMapper;
import com.save_water.iam.infrastructure.persistence.user.UserJpaRepository;


// infrastructure/persistence/UserRepositoryAdapter.java
@Component
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository jpaRepository;
    private final UserMapper mapper;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository, UserMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(mapper::toDomain);
    }
}