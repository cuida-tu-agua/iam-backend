package com.sywater.ms_iam.application.usecase;

import com.sywater.ms_iam.domain.exception.EmailAlreadyExistsException;
import com.sywater.ms_iam.infrastructure.persistence.entity.UserCredentialJpaEntity;
import com.sywater.ms_iam.infrastructure.persistence.entity.UserJpaEntity;
import com.sywater.ms_iam.infrastructure.persistence.repository.UserCredentialRepository;
import com.sywater.ms_iam.infrastructure.persistence.repository.UserRepository;
import com.sywater.ms_iam.infrastructure.security.PasswordHasher;
import com.sywater.ms_iam.infrastructure.security.PasswordValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class RegisterUserUseCase {
    private final UserRepository userRepository;
    private final UserCredentialRepository credentialRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordValidator passwordValidator;

    // Patrón para validar email según RFC 5322 (simplificado)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public RegisterUserUseCase(
            UserRepository userRepository,
            UserCredentialRepository credentialRepository,
            PasswordHasher passwordHasher,
            PasswordValidator passwordValidator
    ) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passwordHasher = passwordHasher;
        this.passwordValidator = passwordValidator;
    }

    @Transactional
    public RegisterResponse execute(RegisterRequest request) {
        // Validaciones de entrada
        validateRequest(request);

        // Validar que el email no exista
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyExistsException(request.email());
        }

        // Validar política de contraseña
        if (!passwordValidator.isValid(request.password())) {
            throw new IllegalArgumentException(
                    "Password must be at least 8 characters, contain uppercase, lowercase, digit, and special character"
            );
        }

        // Crear usuario
        UserJpaEntity user = new UserJpaEntity(
                request.email(),
                request.firstName(),
                request.lastName()
        );
        UserJpaEntity savedUser = userRepository.save(user);

        // Crear credencial con contraseña hasheada - convertir Long a String
        String hashedPassword = passwordHasher.hash(request.password());
        UserCredentialJpaEntity credential = new UserCredentialJpaEntity(
                String.valueOf(savedUser.getId()),
                hashedPassword
        );
        credentialRepository.save(credential);

        return new RegisterResponse(
                String.valueOf(savedUser.getId()),
                savedUser.getEmail(),
                savedUser.getFirstName(),
                savedUser.getLastName()
        );
    }

    private void validateRequest(RegisterRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (!EMAIL_PATTERN.matcher(request.email()).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
        if (request.firstName() == null || request.firstName().isBlank() || request.firstName().length() > 100) {
            throw new IllegalArgumentException("First name must be between 1 and 100 characters");
        }
        if (request.lastName() == null || request.lastName().isBlank() || request.lastName().length() > 100) {
            throw new IllegalArgumentException("Last name must be between 1 and 100 characters");
        }
        if (request.password() == null || request.password().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
    }

    public record RegisterRequest(
            String email,
            String firstName,
            String lastName,
            String password
    ) {}

    public record RegisterResponse(
            String userId,
            String email,
            String firstName,
            String lastName
    ) {}
}
