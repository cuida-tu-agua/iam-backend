package com.sywater.ms_iam.application.port.out;

import com.sywater.ms_iam.application.dto.PageView;
import com.sywater.ms_iam.domain.model.AccountStatus;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.PhoneNumber;
import com.sywater.ms_iam.domain.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(Email email);

    Optional<User> findByPhone(PhoneNumber phone);

    boolean existsByEmail(Email email);

    boolean phoneTakenByOther(PhoneNumber phone, UUID exceptUserId);

    void create(User user);

    void update(User user);

    /**
     * HU-059: not deleted accounts, newest first. text = part of the name or e-mail (blank = all); status null = all.
     * Pages start at 0.
     */
    PageView<User> search(String text, AccountStatus status, int page, int size);
}
