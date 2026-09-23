package com.sywater.ms_iam.infrastructure.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Componente para logging de auditoría de intentos de autenticación.
 * Rastrear: registros exitosos, login exitosos, intentos fallidos.
 */
@Component
public class AuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    public void logRegistrationAttempt(String email, boolean success, String reason) {
        String status = success ? "SUCCESS" : "FAILED";
        auditLog.info("REGISTRATION {} - Email: {}, Reason: {}, Timestamp: {}",
                status, email, reason, Instant.now());
    }

    public void logLoginAttempt(String email, boolean success, String reason) {
        String status = success ? "SUCCESS" : "FAILED";
        auditLog.warn("LOGIN {} - Email: {}, Reason: {}, Timestamp: {}",
                status, email, reason, Instant.now());
    }

    public void logInvalidCredentials(String email) {
        auditLog.warn("INVALID_CREDENTIALS - Email: {}, Timestamp: {}", email, Instant.now());
    }

    public void logDuplicateEmail(String email) {
        auditLog.warn("DUPLICATE_EMAIL - Email: {}, Timestamp: {}", email, Instant.now());
    }
}
