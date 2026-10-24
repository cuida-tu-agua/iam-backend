package com.sywater.ms_iam.application.port.in;

import java.time.Instant;
import java.util.List;

/** ms-places asks ms-iam (which owns the mail server) to write and send the invitation to collaborate in a place. */
public interface InvitationEmailUseCase {

    /**
     * @throws com.sywater.ms_iam.domain.exception.InvalidEmailException the address is not an e-mail
     */
    void send(Command command);

    record Command(String to, String placeName, String invitedByName, List<String> permissions, Instant expiresAt) {}
}
