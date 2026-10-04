package com.sywater.ms_iam.application.port.in;

import com.sywater.ms_iam.application.dto.CodeSent;
import com.sywater.ms_iam.application.dto.RequestContext;

import java.util.UUID;

public interface ActionCodeUseCase {

    enum Action { VALVE_CLOSE }

    CodeSent requestCode(UUID userId, String action, RequestContext context);

    void verifyCode(UUID userId, String action, String code, RequestContext context);
}
