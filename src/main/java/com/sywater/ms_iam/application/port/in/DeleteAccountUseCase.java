package com.sywater.ms_iam.application.port.in;

import com.sywater.ms_iam.application.dto.RequestContext;

import java.util.UUID;

public interface DeleteAccountUseCase {

    void deleteAccount(UUID userId, String password, RequestContext context);
}
