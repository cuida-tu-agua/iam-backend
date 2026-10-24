package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.port.in.InvitationEmailUseCase;
import com.sywater.ms_iam.application.port.out.NotificationSender;
import com.sywater.ms_iam.domain.model.Email;

import java.util.List;

public class InvitationEmailService implements InvitationEmailUseCase {

    private final NotificationSender notifications;

    public InvitationEmailService(NotificationSender notifications) {
        this.notifications = notifications;
    }

    @Override
    public void send(Command command) {
        Email to = new Email(command.to());   // InvalidEmailException if it is not an address
        String place = command.placeName() == null || command.placeName().isBlank() ? "un lugar" : command.placeName().trim();
        List<String> permissions = command.permissions() == null ? List.of() : List.copyOf(command.permissions());
        notifications.sendCollaboratorInvitation(to, place, command.invitedByName(), permissions, command.expiresAt());
    }
}
