package com.sywater.ms_iam.application.usecase;

import com.nimbusds.jose.JOSEException;
import com.sywater.ms_iam.domain.exception.InvalidCredentialsException;
import com.sywater.ms_iam.infrastructure.persistence.entity.UserCredentialJpaEntity;
import com.sywater.ms_iam.infrastructure.persistence.entity.UserJpaEntity;
import com.sywater.ms_iam.infrastructure.persistence.repository.UserCredentialRepository;
import com.sywater.ms_iam.infrastructure.persistence.repository.UserRepository;
import com.sywater.ms_iam.infrastructure.security.JwtTokenIssuer;
import com.sywater.ms_iam.infrastructure.security.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoginUseCase {

    private static final Logger logger = LoggerFactory.getLogger(LoginUseCase.class);

    private final UserRepository userRepository;
    private final UserCredentialRepository credentialRepository;
    private final PasswordHasher passwordHasher;
    private final JwtTokenIssuer tokenIssuer;

    public LoginUseCase(
            UserRepository userRepository,
            UserCredentialRepository credentialRepository,
            PasswordHasher passwordHasher,
            JwtTokenIssuer tokenIssuer
    ) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
    }

    public LoginResponse execute(LoginRequest request) throws JOSEException {
        // Validar entrada
        if (request.email() == null || request.email().isBlank()) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // Buscar usuario por email
        UserJpaEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        UserCredentialJpaEntity credential = credentialRepository.findByUserId(user.getId())
                .orElseThrow(() -> {
                    logger.error("Credential not found for user: {}", user.getId());
                    return new InvalidCredentialsException("Invalid email or password");
                });

        // Validar contraseña
        if (!passwordHasher.matches(request.password(), credential.getPasswordHash())) {
            logger.warn("Invalid password attempt for user: {}", user.getEmail());
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String accessToken = tokenIssuer.issueAccessToken(String.valueOf(user.getId()), user.getEmail());

        logger.debug("JWT token issued for user: {}", user.getId());

        return new LoginResponse(
                String.valueOf(user.getId()),
                user.getEmail(),
                accessToken
        );
    }

    public record LoginRequest(String email, String password) {}

    public record LoginResponse(
            String userId,
            String email,
            String accessToken
    ) {}
}
