package com.sywater.ms_iam.presentation;

import com.sywater.ms_iam.application.dto.AdminUserView;
import com.sywater.ms_iam.application.dto.PageView;
import com.sywater.ms_iam.application.port.in.BlockUserUseCase;
import com.sywater.ms_iam.application.port.in.ListUsersUseCase;
import com.sywater.ms_iam.domain.exception.InvalidFilterException;
import com.sywater.ms_iam.domain.exception.CannotBlockSelfException;
import com.sywater.ms_iam.domain.exception.NotAdministratorException;
import com.sywater.ms_iam.domain.model.AccountStatus;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.Role;
import com.sywater.ms_iam.domain.model.User;
import com.sywater.ms_iam.infrastructure.config.IamProperties;
import com.sywater.ms_iam.infrastructure.config.SecurityConfig;
import com.sywater.ms_iam.infrastructure.security.NimbusAccessTokenIssuer;
import com.sywater.ms_iam.infrastructure.security.RedisTokenRevocationStore;
import com.sywater.ms_iam.presentation.controller.AdminUserController;
import com.sywater.ms_iam.presentation.error.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of /api/admin: only the ADMIN role of a real RS256 token gets in, and the error codes are stable.
 * The use case is a mock; its rules are covered by UserBlockingTest.
 */
@WebMvcTest(controllers = AdminUserController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, AdminUserHttpTest.Keys.class})
class AdminUserHttpTest {

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

    @MockitoBean BlockUserUseCase blocking;
    @MockitoBean ListUsersUseCase listing;
    @MockitoBean RedisTokenRevocationStore revocations;

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String bearer(UUID userId, Role... roles) {
        User user = User.restore(userId, "Ana", "Ruiz", new Email("ana@mail.com"), null, null, true, null, null,
                null, Instant.now(), Instant.now(), Set.of(roles));
        return "Bearer " + new NimbusAccessTokenIssuer((RSAPrivateKey) KEYS.getPrivate(), PROPERTIES).issue(user, Instant.now()).value();
    }

    private static AdminUserView view(UUID id, AccountStatus status, UUID blockedBy) {
        return new AdminUserView(id, "Juan", "Ome", "juan@mail.com", null, status, Instant.parse("2026-09-01T10:00:00Z"),
                blockedBy == null ? null : Instant.parse("2026-10-08T10:00:00Z"), blockedBy);
    }

    // ── HU-059 ───────────────────────────────────────────────────────────

    @Test
    void the_list_passes_search_status_and_page_and_answers_with_the_totals() throws Exception {
        UUID admin = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        given(listing.list(eq(admin), eq("ana"), eq(AccountStatus.BLOCKED), eq(1), eq(10)))
                .willReturn(PageView.of(List.of(view(user, AccountStatus.BLOCKED, admin)), 1, 10, 11));

        mvc.perform(get("/api/admin/users").queryParam("search", "ana").queryParam("status", "BLOCKED")
                        .queryParam("page", "1").queryParam("size", "10").header("Authorization", bearer(admin, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(user.toString()))
                .andExpect(jsonPath("$.items[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$.items[0].password").doesNotExist())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalItems").value(11))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void the_list_defaults_to_the_first_page_of_20_with_no_filters() throws Exception {
        UUID admin = UUID.randomUUID();
        given(listing.list(eq(admin), isNull(), isNull(), eq(0), eq(20))).willReturn(PageView.of(List.of(), 0, 20, 0));

        mvc.perform(get("/api/admin/users").header("Authorization", bearer(admin, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void the_list_is_for_administrators_only() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(UUID.randomUUID(), Role.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("auth.admin_required"));
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        verifyNoInteractions(listing);
    }

    @Test
    void an_unknown_status_or_a_rejected_filter_is_400_not_500() throws Exception {
        UUID admin = UUID.randomUUID();
        given(listing.list(eq(admin), any(), eq(AccountStatus.DELETED), any(Integer.class), any(Integer.class)))
                .willThrow(new InvalidFilterException("Deleted accounts are not listed."));

        mvc.perform(get("/api/admin/users").queryParam("status", "NOPE").header("Authorization", bearer(admin, Role.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("validation.failed"));
        mvc.perform(get("/api/admin/users").queryParam("status", "DELETED").header("Authorization", bearer(admin, Role.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("validation.invalid_filter"));
        mvc.perform(get("/api/admin/users").queryParam("page", "abc").header("Authorization", bearer(admin, Role.ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void an_administrator_blocks_a_user_and_the_answer_says_who() throws Exception {
        UUID admin = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        given(blocking.block(eq(admin), eq(target), eq("Uso indebido"), any())).willReturn(view(target, AccountStatus.BLOCKED, admin));

        mvc.perform(put("/api/admin/users/" + target + "/block").header("Authorization", bearer(admin, Role.USER, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Uso indebido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.blockedBy").value(admin.toString()))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void the_body_is_optional_and_unblock_works_the_same_way() throws Exception {
        UUID admin = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        given(blocking.unblock(eq(admin), eq(target), isNull(), any())).willReturn(view(target, AccountStatus.ACTIVE, null));

        mvc.perform(put("/api/admin/users/" + target + "/unblock").header("Authorization", bearer(admin, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.blockedAt").doesNotExist());
    }

    @Test
    void a_normal_user_token_is_403_with_the_stable_code_and_never_reaches_the_use_case() throws Exception {
        mvc.perform(put("/api/admin/users/" + UUID.randomUUID() + "/block").header("Authorization", bearer(UUID.randomUUID(), Role.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("auth.admin_required"));
        verifyNoInteractions(blocking);
    }

    @Test
    void without_a_token_the_admin_endpoints_are_401() throws Exception {
        mvc.perform(put("/api/admin/users/" + UUID.randomUUID() + "/block")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/users/" + UUID.randomUUID() + "/unblock")).andExpect(status().isUnauthorized());
    }

    @Test
    void an_admin_token_whose_role_was_removed_in_the_database_is_403() throws Exception {
        UUID admin = UUID.randomUUID();
        given(blocking.block(eq(admin), any(), any(), any())).willThrow(new NotAdministratorException());

        mvc.perform(put("/api/admin/users/" + UUID.randomUUID() + "/block").header("Authorization", bearer(admin, Role.ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("auth.admin_required"));
    }

    @Test
    void blocking_yourself_is_400_and_a_too_long_reason_is_rejected() throws Exception {
        UUID admin = UUID.randomUUID();
        given(blocking.block(eq(admin), eq(admin), any(), any())).willThrow(new CannotBlockSelfException());

        mvc.perform(put("/api/admin/users/" + admin + "/block").header("Authorization", bearer(admin, Role.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("user.cannot_block_self"));
        mvc.perform(put("/api/admin/users/" + UUID.randomUUID() + "/block").header("Authorization", bearer(admin, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("validation.failed"));
    }
}
