package com.sywater.ms_iam.application.port.in;

import com.sywater.ms_iam.application.dto.PhoneCodeSent;
import com.sywater.ms_iam.application.dto.RequestContext;
import com.sywater.ms_iam.application.dto.UserView;

import java.util.UUID;

/** HU-028: verify the phone by SMS or WhatsApp; the mode picked here is the one the critical alerts will use. */
public interface PhoneVerificationUseCase {

    /**
     * @param country CO (Colombia, +57) or EC (Ecuador, +593)
     * @param phone   the national number as the user typed it
     * @param channel SMS or WHATSAPP
     */
    PhoneCodeSent requestCode(UUID userId, String country, String phone, String channel, RequestContext context);

    /** The code is right: the phone and its mode become the verified ones. */
    UserView confirm(UUID userId, String code, RequestContext context);
}
