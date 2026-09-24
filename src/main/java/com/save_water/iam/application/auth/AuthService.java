package com.save_water.iam.application.auth;

import org.springframework.stereotype.Service;

import com.save_water.iam.domain.exception.InvalidCredentialsException;
import com.save_water.iam.domain.model.user.User;
import com.save_water.iam.domain.port.in.loginCase.LoginCommand;
import com.save_water.iam.domain.port.in.loginCase.LoginResult;
import com.save_water.iam.domain.port.in.loginCase.LoginUseCase;
import com.save_water.iam.domain.port.out.loginCase.PasswordEncoderPort;
import com.save_water.iam.domain.port.out.loginCase.TokenGeneratorPort;
import com.save_water.iam.domain.port.out.loginCase.UserRepositoryPort;

@Service
public class AuthService implements LoginUseCase{
    // Authentication service implementation
    private final UserRepositoryPort userRepository;
    private final TokenGeneratorPort tokenGenerator;
    private final PasswordEncoderPort passwordEncoder;

    public AuthService(UserRepositoryPort userRepository, TokenGeneratorPort tokenGenerator, PasswordEncoderPort passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenGenerator = tokenGenerator;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public LoginResult login(LoginCommand command) {
        User user = userRepository.findByEmail(command.email())
                .orElseThrow(() -> new InvalidCredentialsException());

        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = tokenGenerator.generate(user);
        return new LoginResult(token, user.getEmail());
    }
        

}