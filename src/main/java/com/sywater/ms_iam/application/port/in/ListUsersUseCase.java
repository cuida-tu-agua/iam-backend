package com.sywater.ms_iam.application.port.in;

import com.sywater.ms_iam.application.dto.AdminUserView;
import com.sywater.ms_iam.application.dto.PageView;
import com.sywater.ms_iam.domain.model.AccountStatus;

import java.util.UUID;

/** HU-059 */
public interface ListUsersUseCase {

    int DEFAULT_SIZE = 20;
    int MAX_SIZE = 100;

    /**
     * The registered users, newest first. Deleted accounts never appear (their data was erased).
     *
     * @param search part of the first name, last name, full name or e-mail (any case); blank = everybody
     * @param status ACTIVE, BLOCKED or UNVERIFIED; null = every status
     * @throws com.sywater.ms_iam.domain.exception.NotAdministratorException the caller is not an ADMIN
     * @throws com.sywater.ms_iam.domain.exception.InvalidFilterException     unusable status or search text
     */
    PageView<AdminUserView> list(UUID administratorId, String search, AccountStatus status, int page, int size);
}
