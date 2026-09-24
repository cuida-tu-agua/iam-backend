package com.save_water.iam.domain.port.out.loginCase;

import com.save_water.iam.domain.model.user.User;

public interface TokenGeneratorPort {
    String generate(User user);
}