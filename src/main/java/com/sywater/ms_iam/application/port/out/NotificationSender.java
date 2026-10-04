package com.sywater.ms_iam.application.port.out;

import com.sywater.ms_iam.domain.model.Email;

import java.time.Duration;

public interface NotificationSender {

    void sendVerificationCode(Email to, String firstName, String code, Duration validFor);

    void sendPasswordResetCode(Email to, String firstName, String code, Duration validFor);

    void sendPasswordChanged(Email to, String firstName);
}
