package com.sywater.ms_iam.application.port.out;

import com.sywater.ms_iam.domain.model.PhoneChannel;
import com.sywater.ms_iam.domain.model.PhoneNumber;

import java.time.Duration;

/**
 * HU-028: ms-notification delivers short messages (SMS / WhatsApp through Twilio). IAM only asks it to deliver the code
 * that verifies a phone: one service talks to the provider, keeps its credentials and pays for the messages.
 */
public interface ShortMessageGateway {

    /**
     * Sends the code at once by the mode the user picked.
     *
     * @throws com.sywater.ms_iam.domain.exception.PhoneNotReachableException the provider refused the number for that mode
     * @throws com.sywater.ms_iam.domain.exception.ExternalServiceUnavailableException ms-notification or the provider is down
     */
    void sendVerificationCode(PhoneNumber to, PhoneChannel channel, String code, Duration validFor);
}
