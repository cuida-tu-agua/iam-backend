package com.save_water.iam.domain.port.out.loginCase;
import java.util.Optional;

import com.save_water.iam.domain.model.user.User;


public interface UserRepositoryPort {
    Optional<User> findByEmail(String email);
}