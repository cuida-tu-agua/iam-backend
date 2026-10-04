package com.sywater.ms_iam.presentation.controller;

import com.sywater.ms_iam.application.dto.UserView;
import com.sywater.ms_iam.application.port.in.DeleteAccountUseCase;
import com.sywater.ms_iam.application.port.in.ProfileUseCase;
import com.sywater.ms_iam.domain.exception.InvalidAvatarException;
import com.sywater.ms_iam.presentation.dto.ProfileRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me")
public class ProfileController {

    private final ProfileUseCase profile;
    private final DeleteAccountUseCase deletion;

    public ProfileController(ProfileUseCase profile, DeleteAccountUseCase deletion) {
        this.profile = profile;
        this.deletion = deletion;
    }

    @GetMapping
    public UserView get(@AuthenticationPrincipal Jwt jwt) {
        return profile.getProfile(userId(jwt));
    }

    @PatchMapping
    public UserView update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequests.UpdateProfile body,
                           HttpServletRequest request) {
        return profile.updateProfile(userId(jwt),
                new ProfileUseCase.UpdateCommand(body.firstName(), body.lastName(), body.phone()),
                RequestContexts.from(request));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequests.ChangePassword body,
                               HttpServletRequest request) {
        profile.changePassword(userId(jwt), body.currentPassword(), body.newPassword(), RequestContexts.from(request));
    }

    @PutMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserView changeAvatar(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file,
                                 HttpServletRequest request) {
        try {
            return profile.changeAvatar(userId(jwt), file.getBytes(), file.getContentType(), RequestContexts.from(request));
        } catch (IOException e) {
            throw new InvalidAvatarException("The photo could not be read.");
        }
    }

    @DeleteMapping("/avatar")
    public UserView removeAvatar(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return profile.removeAvatar(userId(jwt), RequestContexts.from(request));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequests.DeleteAccount body,
                              HttpServletRequest request) {
        deletion.deleteAccount(userId(jwt), body.password(), RequestContexts.from(request));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
