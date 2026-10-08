package com.sywater.ms_iam.application.port.in;

import com.sywater.ms_iam.application.dto.AdminUserView;
import com.sywater.ms_iam.application.dto.RequestContext;

import java.util.UUID;

/** HU-060 */
public interface BlockUserUseCase {

    /**
     * Blocks the account: no more logins, and every session of that user dies at once (refresh tokens and the
     * access tokens already issued). Idempotent.
     *
     * @param reason optional note for the audit log
     * @throws com.sywater.ms_iam.domain.exception.NotAdministratorException the caller is not an ADMIN
     * @throws com.sywater.ms_iam.domain.exception.CannotBlockSelfException  an administrator blocked themselves
     * @throws com.sywater.ms_iam.domain.exception.UserNotFoundException     unknown or deleted account
     */
    AdminUserView block(UUID administratorId, UUID userId, String reason, RequestContext context);

    AdminUserView unblock(UUID administratorId, UUID userId, String reason, RequestContext context);
}
