package com.sywater.ms_iam.presentation;

import com.sywater.ms_iam.application.dto.CodeSent;
import com.sywater.ms_iam.application.dto.UserView;
import com.sywater.ms_iam.application.port.in.*;
import com.sywater.ms_iam.domain.exception.AccountLockedException;
import com.sywater.ms_iam.domain.exception.CodeRecentlySentException;
import com.sywater.ms_iam.domain.exception.WeakPasswordException;
import com.sywater.ms_iam.domain.exception.WrongPasswordException;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.User;
import com.sywater.ms_iam.infrastructure.config.IamProperties;
import com.sywater.ms_iam.infrastructure.config.SecurityConfig;
import com.sywater.ms_iam.infrastructure.security.NimbusAccessTokenIssuer;
import com.sywater.ms_iam.infrastructure.security.RedisTokenRevocationStore;
import com.sywater.ms_iam.presentation.controller.ActionCodeController;
import com.sywater.ms_iam.presentation.controller.AuthController;
import com.sywater.ms_iam.presentation.controller.ProfileController;
import com.sywater.ms_iam.presentation.controller.InternalUserController;
import com.sywater.ms_iam.infrastructure.security.InternalKeyGuard;
import com.sywater.ms_iam.application.dto.ContactView;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import org.springframework.test.context.TestPropertySource;
import com.sywater.ms_iam.presentation.error.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract the app depends on: status codes, Problem Details codes, extra fields and
 * the JWT checks (real RS256 signature with a test key pair, issuer, denylist).
 * Use cases are mocks: their logic is covered by the application tests.
 */
@WebMvcTest(controllers = {AuthController.class, ProfileController.class, ActionCodeController.class, InternalUserController.class, com.sywater.ms_iam.presentation.controller.InternalInvitationController.class,
        com.sywater.ms_iam.presentation.controller.PhoneVerificationController.class})
@TestPropertySource(properties = "iam.internal.api-key=test-internal-key-0123456789")
@Import({SecurityConfig.class, ApiExceptionHandler.class, InternalKeyGuard.class, HttpContractTest.Keys.class})
class HttpContractTest {

    private static final KeyPair KEYS = generateKeys();
    private static final IamProperties PROPERTIES = new IamProperties(
            new IamProperties.Jwt("https://iam.cuidatuagua.local", "iam-key-1", Duration.ofHours(1), Duration.ofDays(7), null, null),
            new IamProperties.Codes(Duration.ofHours(24), Duration.ofMinutes(15), 5, Duration.ofSeconds(60)),
            new IamProperties.Lockout(5, Duration.ofMinutes(15)),
            new IamProperties.Avatars("target/test-avatars", 2_097_152),
            new IamProperties.Mail("test@local"),
            List.of("http://localhost:19006"));

    @TestConfiguration
    static class Keys {
        @Bean RSAPublicKey rsaPublicKey() { return (RSAPublicKey) KEYS.getPublic(); }
        @Bean @Primary IamProperties testIamProperties() { return PROPERTIES; }
    }

    @Autowired MockMvc mvc;

    @MockitoBean RegisterUserUseCase register;
    @MockitoBean EmailVerificationUseCase verification;
    @MockitoBean LoginUseCase login;
    @MockitoBean SessionUseCase sessions;
    @MockitoBean PasswordRecoveryUseCase recovery;
    @MockitoBean ProfileUseCase profile;
    @MockitoBean GoogleSignInUseCase googleSignIn;
    @MockitoBean DeleteAccountUseCase deletion;
    @MockitoBean RedisTokenRevocationStore revocations;
    @MockitoBean ActionCodeUseCase actionCodes;
    @MockitoBean ContactLookupUseCase contactLookup;
    @MockitoBean com.sywater.ms_iam.application.port.in.InvitationEmailUseCase invitationEmails;
    @MockitoBean PhoneVerificationUseCase phoneVerification;

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String tokenFor(UUID userId, IamProperties properties) {
        User user = User.restore(userId, "Juan", "Ome", new Email("juan@mail.com"), null, null, true, null, null,
                null, Instant.now(), Instant.now(), Set.of(com.sywater.ms_iam.domain.model.Role.USER));
        return new NimbusAccessTokenIssuer((RSAPrivateKey) KEYS.getPrivate(), properties).issue(user, Instant.now()).value();
    }

    // ── Public endpoints ─────────────────────────────────────────────────

    @Test
    void register_returns_201_with_the_masked_email() throws Exception {
        given(register.register(any())).willReturn(new CodeSent("ju**@mail.com", Instant.parse("2026-09-30T15:00:00Z")));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"Juan","lastName":"Ome","email":"juan@mail.com","password":"Agua2026!"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedEmail").value("ju**@mail.com"))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-30T15:00:00Z"));
    }

    @Test
    void missing_fields_give_400_with_one_error_per_field() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"juan@mail.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("validation.failed"))
                .andExpect(jsonPath("$.errors.firstName").isArray())
                .andExpect(jsonPath("$.errors.password").isArray());
    }

    @Test
    void weak_password_lists_the_unmet_rules() throws Exception {
        given(register.register(any())).willThrow(new WeakPasswordException(List.of("UPPERCASE", "SPECIAL")));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"Juan","lastName":"Ome","email":"juan@mail.com","password":"agua2026"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("user.weak_password"))
                .andExpect(jsonPath("$.unmetRules[0]").value("UPPERCASE"));
    }

    @Test
    void wrong_password_is_401_with_remaining_attempts() throws Exception {
        given(login.login(anyString(), anyString(), any())).willThrow(new WrongPasswordException(3));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"juan@mail.com\",\"password\":\"Mala2026!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("auth.wrong_password"))
                .andExpect(jsonPath("$.remainingAttempts").value(3));
    }

    @Test
    void locked_account_is_423_with_the_end_of_the_lock() throws Exception {
        given(login.login(anyString(), anyString(), any()))
                .willThrow(new AccountLockedException(Instant.parse("2026-09-29T15:15:00Z")));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"juan@mail.com\",\"password\":\"x\"}"))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.title").value("auth.account_locked"))
                .andExpect(jsonPath("$.lockedUntil").value("2026-09-29T15:15:00Z"));
    }

    @Test
    void resend_too_soon_is_429_with_retry_after() throws Exception {
        given(verification.resend(anyString())).willThrow(new CodeRecentlySentException(Duration.ofSeconds(42)));

        mvc.perform(post("/api/auth/verify-email/resend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"juan@mail.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(42));
    }

    // ── Protected endpoints ──────────────────────────────────────────────

    @Test
    void profile_without_token_is_401_problem_json() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("auth.invalid_token"));
    }

    @Test
    void profile_with_a_valid_token_uses_the_sub_claim() throws Exception {
        UUID id = UUID.randomUUID();
        given(profile.getProfile(id)).willReturn(new UserView(id, "Juan", "Ome", "juan@mail.com", null, false, null, null,
                true, Set.of("USER"), Instant.parse("2026-09-01T00:00:00Z")));

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tokenFor(id, PROPERTIES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void revoked_token_is_rejected() throws Exception {
        given(revocations.isRevoked(anyString(), anyString(), any())).willReturn(true);

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tokenFor(UUID.randomUUID(), PROPERTIES)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void token_from_another_issuer_is_rejected() throws Exception {
        IamProperties other = new IamProperties(new IamProperties.Jwt("https://evil.example", "k", Duration.ofHours(1),
                Duration.ofDays(7), null, null), PROPERTIES.codes(), PROPERTIES.lockout(), PROPERTIES.avatars(),
                PROPERTIES.mail(), PROPERTIES.corsAllowedOrigins());

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tokenFor(UUID.randomUUID(), other)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_passes_the_jti_and_the_refresh_token() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + tokenFor(id, PROPERTIES))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"r-1\"}"))
                .andExpect(status().isNoContent());

        verify(sessions).logout(eq(id), anyString(), any(Instant.class), eq("r-1"), any());
    }

    @Test
    void logout_without_body_still_works() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + tokenFor(id, PROPERTIES)))
                .andExpect(status().isNoContent());

        verify(sessions).logout(eq(id), anyString(), any(Instant.class), isNull(), any());
    }

    // ── /internal (service-to-service) ───────────────────────────────────

    @Test
    void internal_contact_needs_the_shared_key_and_never_a_user_token() throws Exception {
        UUID id = UUID.randomUUID();
        given(contactLookup.findContact(id)).willReturn(new ContactView(id, "juan@mail.com", "Juan Ome"));

        mvc.perform(get("/internal/users/" + id + "/contact").header("X-Internal-Key", "test-internal-key-0123456789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("juan@mail.com"))
                .andExpect(jsonPath("$.fullName").value("Juan Ome"));
    }

    @Test
    void internal_contact_carries_the_verified_phone_and_its_mode_for_ms_notification() throws Exception {
        UUID id = UUID.randomUUID();
        given(contactLookup.findContact(id)).willReturn(new ContactView(id, "juan@mail.com", "Juan Ome", "+573001112233", true, "WHATSAPP"));

        mvc.perform(get("/internal/users/" + id + "/contact").header("X-Internal-Key", "test-internal-key-0123456789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+573001112233"))
                .andExpect(jsonPath("$.phoneVerified").value(true))
                .andExpect(jsonPath("$.phoneChannel").value("WHATSAPP"));
    }

    @Test
    void internal_contact_by_email_finds_the_account_for_ms_places() throws Exception {
        UUID id = UUID.randomUUID();
        given(contactLookup.findContactByEmail("ana@mail.com")).willReturn(new ContactView(id, "ana@mail.com", "Ana Gil"));

        mvc.perform(get("/internal/users/by-email").param("email", "ana@mail.com")
                        .header("X-Internal-Key", "test-internal-key-0123456789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.fullName").value("Ana Gil"));
    }

    @Test
    void internal_contact_by_email_is_404_when_nobody_has_it_and_403_without_the_key() throws Exception {
        given(contactLookup.findContactByEmail("nadie@mail.com")).willThrow(new UserNotFoundException());

        mvc.perform(get("/internal/users/by-email").param("email", "nadie@mail.com")
                        .header("X-Internal-Key", "test-internal-key-0123456789"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/users/by-email").param("email", "nadie@mail.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("auth.internal_only"));
    }

    @Test
    void the_invitation_email_is_accepted_with_the_internal_key() throws Exception {
        mvc.perform(post("/internal/invitation-emails").header("X-Internal-Key", "test-internal-key-0123456789")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitationId\":\"" + UUID.randomUUID() + "\",\"to\":\"ana@mail.com\",\"placeName\":\"Casa\","
                                + "\"invitedByName\":\"Olga\",\"permissions\":[\"VIEW_CONSUMPTION\"],\"expiresAt\":\"2026-10-16T15:00:00Z\"}"))
                .andExpect(status().isAccepted());
        org.mockito.Mockito.verify(invitationEmails).send(any());
    }

    @Test
    void the_invitation_email_needs_the_key_and_a_valid_body() throws Exception {
        String body = "{\"to\":\"ana@mail.com\",\"placeName\":\"Casa\",\"permissions\":[],\"expiresAt\":\"2026-10-16T15:00:00Z\"}";
        mvc.perform(post("/internal/invitation-emails").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/invitation-emails").header("X-Internal-Key", "test-internal-key-0123456789")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"\",\"placeName\":\"Casa\"}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(invitationEmails);
    }

    // ── HU-028: verify the phone ─────────────────────────────────────────

    @Test
    void asking_for_a_phone_code_returns_201_with_the_masked_number_and_the_mode() throws Exception {
        UUID id = UUID.randomUUID();
        given(phoneVerification.requestCode(eq(id), eq("CO"), eq("3001112233"), eq("WHATSAPP"), any()))
                .willReturn(new com.sywater.ms_iam.application.dto.PhoneCodeSent("+57300***233", "WHATSAPP", Instant.parse("2026-10-22T15:05:00Z")));

        mvc.perform(post("/api/users/me/phone-verification").header("Authorization", "Bearer " + tokenFor(id, PROPERTIES))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"country":"CO","phone":"3001112233","channel":"WHATSAPP"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedPhone").value("+57300***233"))
                .andExpect(jsonPath("$.channel").value("WHATSAPP"));
    }

    @Test
    void a_number_that_does_not_work_for_the_mode_is_422_and_a_missing_field_400() throws Exception {
        UUID id = UUID.randomUUID();
        given(phoneVerification.requestCode(eq(id), any(), any(), any(), any()))
                .willThrow(new com.sywater.ms_iam.domain.exception.PhoneNotReachableException("WHATSAPP"));

        mvc.perform(post("/api/users/me/phone-verification").header("Authorization", "Bearer " + tokenFor(id, PROPERTIES))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"country":"CO","phone":"3001112233","channel":"WHATSAPP"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("phone.not_reachable"));

        mvc.perform(post("/api/users/me/phone-verification").header("Authorization", "Bearer " + tokenFor(id, PROPERTIES))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"country\":\"CO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").isArray())
                .andExpect(jsonPath("$.errors.channel").isArray());
    }

    @Test
    void confirming_the_phone_returns_the_profile_and_without_a_token_it_is_401() throws Exception {
        UUID id = UUID.randomUUID();
        given(phoneVerification.confirm(eq(id), eq("123456"), any())).willReturn(new UserView(id, "Juan", "Ome", "juan@mail.com",
                "+593991112233", true, "SMS", null, true, Set.of("USER"), Instant.parse("2026-09-01T00:00:00Z")));

        mvc.perform(post("/api/users/me/phone-verification/confirm").header("Authorization", "Bearer " + tokenFor(id, PROPERTIES))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneVerified").value(true))
                .andExpect(jsonPath("$.phoneChannel").value("SMS"));

        mvc.perform(post("/api/users/me/phone-verification/confirm").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internal_contact_without_or_with_a_wrong_key_is_403_and_a_user_token_is_not_enough() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(get("/internal/users/" + id + "/contact"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("auth.internal_only"));
        mvc.perform(get("/internal/users/" + id + "/contact").header("X-Internal-Key", "another-key-0123456789abcd"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/users/" + id + "/contact").header("Authorization", "Bearer " + tokenFor(id, PROPERTIES)))
                .andExpect(status().isForbidden());
    }

    @Test
    void internal_contact_of_an_unknown_user_is_404_with_the_stable_code() throws Exception {
        UUID id = UUID.randomUUID();
        given(contactLookup.findContact(id)).willThrow(new UserNotFoundException());

        mvc.perform(get("/internal/users/" + id + "/contact").header("X-Internal-Key", "test-internal-key-0123456789"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("user.not_found"));
    }
}
