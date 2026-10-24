package com.sywater.ms_iam.application.port.out;

import com.sywater.ms_iam.domain.model.Email;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface NotificationSender {

    void sendVerificationCode(Email to, String firstName, String code, Duration validFor);

    void sendPasswordResetCode(Email to, String firstName, String code, Duration validFor);

    void sendPasswordChanged(Email to, String firstName);

    void sendActionCode(Email to, String firstName, String action, String code, Duration validFor);

    /** A place owner invites {@code to} to collaborate (HU-042). The person may not have an account yet. */
    void sendCollaboratorInvitation(Email to, String placeName, String invitedByName, List<String> permissions, Instant expiresAt);
}
