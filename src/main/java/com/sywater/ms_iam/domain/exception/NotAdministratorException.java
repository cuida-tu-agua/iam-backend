package com.sywater.ms_iam.domain.exception;

/** The caller does not hold the ADMIN role (checked again in the database, not only in the token). HTTP 403. */
public class NotAdministratorException extends DomainException {
    public NotAdministratorException() {
        super("auth.admin_required", "Only an administrator can do this.");
    }
}
