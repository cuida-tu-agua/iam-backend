package com.sywater.ms_iam.application.port.in;

import com.sywater.ms_iam.application.dto.RequestContext;
import com.sywater.ms_iam.application.dto.UserView;

import java.util.UUID;

public interface ProfileUseCase {

    record UpdateCommand(String firstName, String lastName, String phone) {}

    UserView getProfile(UUID userId);

    UserView updateProfile(UUID userId, UpdateCommand command, RequestContext context);

    void changePassword(UUID userId, String currentPassword, String newPassword, RequestContext context);

    UserView changeAvatar(UUID userId, byte[] content, String contentType, RequestContext context);

    UserView removeAvatar(UUID userId, RequestContext context);
}
