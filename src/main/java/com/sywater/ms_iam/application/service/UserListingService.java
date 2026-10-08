package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.AdminUserView;
import com.sywater.ms_iam.application.dto.PageView;
import com.sywater.ms_iam.application.port.in.ListUsersUseCase;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.InvalidFilterException;
import com.sywater.ms_iam.domain.model.AccountStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public class UserListingService implements ListUsersUseCase {

    static final int SEARCH_MAX = 100;

    private final UserRepository users;
    private final AdminAccess admins;

    public UserListingService(UserRepository users, AdminAccess admins) {
        this.users = users;
        this.admins = admins;
    }

    @Override
    @Transactional(readOnly = true)
    public PageView<AdminUserView> list(UUID administratorId, String search, AccountStatus status, int page, int size) {
        admins.require(administratorId);

        if (status == AccountStatus.DELETED) {
            throw new InvalidFilterException("Deleted accounts are not listed: their data was erased.");
        }
        String text = search == null ? "" : search.trim();
        if (text.length() > SEARCH_MAX) {
            throw new InvalidFilterException("The search text has at most " + SEARCH_MAX + " characters.");
        }

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return users.search(text, status, safePage, safeSize).map(AdminUserView::of);
    }
}
