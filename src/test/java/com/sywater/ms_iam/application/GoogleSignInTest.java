package com.sywater.ms_iam.application;

import com.sywater.ms_iam.application.dto.AuthResult;
import com.sywater.ms_iam.application.port.in.RegisterUserUseCase;
import com.sywater.ms_iam.application.port.out.GoogleIdentityProvider.GoogleIdentity;
import com.sywater.ms_iam.domain.exception.GoogleSignInDisabledException;
import com.sywater.ms_iam.domain.exception.GoogleSignInFailedException;
import com.sywater.ms_iam.domain.exception.InvalidRedirectException;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.Role;
import com.sywater.ms_iam.domain.model.User;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-004: register and sign in with Google (authorization code flow, the provider is a fake). */
class GoogleSignInTest {

    private static final String WEB = "https://sy-water.tech/";

    private final TestWorld w = new TestWorld();

    /** Runs start + Google's callback and returns the query of the address the app receives. */
    private Map<String, String> signInThroughGoogle(String appRedirect, String authorizationCode) {
        URI google = w.googleSignIn.start(appRedirect);
        String state = google.getQuery().substring("state=".length());
        URI back = w.googleSignIn.finish(authorizationCode, state, null, TestWorld.CTX);
        return query(back);
    }

    private static Map<String, String> query(URI uri) {
        return Arrays.stream(uri.getRawQuery().split("&")).map(p -> p.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
    }

    private User user(String email) {
        return w.users.findByEmail(new Email(email)).orElseThrow();
    }

    @Test
    void a_new_google_account_is_created_verified_and_gets_a_normal_session() {
        Map<String, String> back = signInThroughGoogle(WEB, "code-1");

        AuthResult session = w.googleSignIn.exchange(back.get("google_code"), TestWorld.CTX);

        User created = user("juan@gmail.com");
        assertThat(created.isEmailVerified()).isTrue();                 // no e-mail verification for Google accounts
        assertThat(created.roles()).containsExactly(Role.USER);
        assertThat(created.fullName()).isEqualTo("Juan Ome");
        assertThat(w.credentials.googleIds).containsEntry(created.id(), "google-sub-1");
        assertThat(w.credentials.hashes).doesNotContainKey(created.id()); // no password
        assertThat(w.mailbox.sent).isEmpty();                              // nothing was e-mailed
        assertThat(session.user().id()).isEqualTo(created.id());
        assertThat(session.refreshToken()).isNotBlank();
        assertThat(w.activity.actions).contains("REGISTERED_GOOGLE", "LOGIN_GOOGLE");
    }

    @Test
    void an_existing_local_account_is_linked_verified_and_keeps_its_password() {
        w.registration.register(new RegisterUserUseCase.Command("Juan", "Ome", "juan@gmail.com", null, TestWorld.PASSWORD));
        UUID id = user("juan@gmail.com").id();
        assertThat(user("juan@gmail.com").isEmailVerified()).isFalse();

        Map<String, String> back = signInThroughGoogle(WEB, "code-1");
        AuthResult session = w.googleSignIn.exchange(back.get("google_code"), TestWorld.CTX);

        assertThat(session.user().id()).isEqualTo(id);                  // the same account, not a new one
        assertThat(user("juan@gmail.com").isEmailVerified()).isTrue();
        assertThat(w.credentials.googleIds).containsEntry(id, "google-sub-1");
        assertThat(w.activity.actions).contains("GOOGLE_LINKED");
        // HU-004: both methods work for the same account
        assertThat(w.auth.login("juan@gmail.com", TestWorld.PASSWORD, TestWorld.CTX).user().id()).isEqualTo(id);
    }

    @Test
    void the_second_time_the_account_is_found_by_its_google_id_even_if_the_email_changed() {
        signInThroughGoogle(WEB, "code-1");
        UUID id = user("juan@gmail.com").id();
        w.google.identity = new GoogleIdentity("google-sub-1", "nuevo@gmail.com", true, "Juan", "Ome");

        Map<String, String> back = signInThroughGoogle(WEB, "code-2");

        assertThat(w.googleSignIn.exchange(back.get("google_code"), TestWorld.CTX).user().id()).isEqualTo(id);
        assertThat(w.users.byId).hasSize(1);
    }

    @Test
    void the_handoff_code_works_only_once() {
        String code = signInThroughGoogle(WEB, "code-1").get("google_code");
        w.googleSignIn.exchange(code, TestWorld.CTX);

        assertThatThrownBy(() -> w.googleSignIn.exchange(code, TestWorld.CTX))
                .isInstanceOf(GoogleSignInFailedException.class);
        assertThatThrownBy(() -> w.googleSignIn.exchange("invented", TestWorld.CTX))
                .isInstanceOf(GoogleSignInFailedException.class);
    }

    @Test
    void the_state_is_single_use_and_uses_pkce() {
        URI google = w.googleSignIn.start(WEB);
        String state = google.getQuery().substring("state=".length());
        w.googleSignIn.finish("code-1", state, null, TestWorld.CTX);

        assertThat(w.google.usedVerifiers).singleElement().satisfies(v -> assertThat(v).hasSizeGreaterThanOrEqualTo(7));
        assertThatThrownBy(() -> w.googleSignIn.finish("code-1", state, null, TestWorld.CTX))
                .isInstanceOf(GoogleSignInFailedException.class);
        assertThatThrownBy(() -> w.googleSignIn.finish("code-1", "forged", null, TestWorld.CTX))
                .isInstanceOf(GoogleSignInFailedException.class);
    }

    @Test
    void only_the_configured_app_addresses_can_receive_the_result() {
        assertThat(w.googleSignIn.start("https://sy-water.tech/login")).isNotNull();
        assertThat(w.googleSignIn.start("http://localhost:19006/")).isNotNull();
        assertThat(w.googleSignIn.start("saveyourwater://auth/google")).isNotNull();
        assertThat(w.googleSignIn.start("exp://192.168.1.20:8081/--/auth/google")).isNotNull();

        for (String evil : new String[]{"https://sy-water.tech.evil.com/", "https://evil.com/?x=https://sy-water.tech",
                "http://sy-water.tech/", "http://localhost:19007/", "javascript:alert(1)", "", null}) {
            assertThatThrownBy(() -> w.googleSignIn.start(evil)).as(String.valueOf(evil))
                    .isInstanceOf(InvalidRedirectException.class);
        }
    }

    @Test
    void problems_go_back_to_the_app_as_an_error_code() {
        assertThat(signInThroughGoogle(WEB, "bad-code")).containsEntry("google_error", "auth.google_failed");

        w.google.identity = new GoogleIdentity("google-sub-2", "otro@gmail.com", false, "Otro", "Mas");
        assertThat(signInThroughGoogle(WEB, "code-1")).containsEntry("google_error", "auth.google_failed");
        assertThat(w.users.byId).isEmpty();

        URI google = w.googleSignIn.start("saveyourwater://auth/google");
        String state = google.getQuery().substring("state=".length());
        URI cancelled = w.googleSignIn.finish(null, state, "access_denied", TestWorld.CTX);
        assertThat(cancelled.toString()).isEqualTo("saveyourwater://auth/google?google_error=auth.google_cancelled");
    }

    @Test
    void a_blocked_account_cannot_enter_with_google() {
        UUID admin = w.administrator("admin@mail.com");
        UUID juan = w.verifiedUser("juan@gmail.com");
        w.blocking.block(admin, juan, "Uso indebido", TestWorld.CTX);

        assertThat(signInThroughGoogle(WEB, "code-1")).containsEntry("google_error", "auth.account_blocked");
    }

    @Test
    void an_account_linked_to_another_google_account_is_not_taken_over() {
        UUID id = w.verifiedUser("juan@gmail.com");
        w.credentials.googleIds.put(id, "google-sub-OTHER");

        assertThat(signInThroughGoogle(WEB, "code-1")).containsEntry("google_error", "auth.google_account_conflict");
    }

    @Test
    void names_from_google_are_cleaned_and_missing_ones_get_a_fallback() {
        w.google.identity = new GoogleIdentity("google-sub-3", "maria.jose_99@gmail.com", true, "😀 2", null);

        w.googleSignIn.exchange(signInThroughGoogle(WEB, "code-1").get("google_code"), TestWorld.CTX);

        User created = user("maria.jose_99@gmail.com");
        assertThat(created.firstName()).isEqualTo("Maria jose");
        assertThat(created.lastName()).isEqualTo("Sin apellido");
    }

    @Test
    void without_configuration_the_feature_is_off() {
        w.google.configured = false;

        assertThat(w.googleSignIn.enabled()).isFalse();
        assertThatThrownBy(() -> w.googleSignIn.start(WEB)).isInstanceOf(GoogleSignInDisabledException.class);
    }

    /** Signs in with Google, then starts the re-confirmation of that account and returns its google_reauth code. */
    private String reconfirmWithGoogle(UUID userId, String authorizationCode) {
        URI google = w.googleSignIn.startReauth(userId, WEB);
        String state = google.getQuery().substring("state=".length());
        Map<String, String> back = query(w.googleSignIn.finish(authorizationCode, state, null, TestWorld.CTX));
        return back.get("google_reauth");
    }

    @Test
    void a_google_account_is_deleted_only_after_confirming_it_with_its_google_account() {
        signInThroughGoogle(WEB, "code-1");
        UUID id = user("juan@gmail.com").id();

        assertThatThrownBy(() -> w.deletion.deleteAccount(id, null, null, TestWorld.CTX))
                .isInstanceOf(GoogleSignInFailedException.class);             // no password, no code: refused
        assertThat(user("juan@gmail.com").isDeleted()).isFalse();

        String code = reconfirmWithGoogle(id, "code-2");
        assertThat(code).isNotBlank();
        assertThatThrownBy(() -> w.googleSignIn.exchange(code, TestWorld.CTX))
                .isInstanceOf(GoogleSignInFailedException.class);             // a re-confirmation is not a login code

        w.deletion.deleteAccount(id, null, code, TestWorld.CTX);
        assertThat(w.users.findById(id).orElseThrow().isDeleted()).isTrue();
        assertThat(w.activity.actions).contains("ACCOUNT_DELETED");
    }

    @Test
    void the_re_confirmation_code_works_once_and_only_for_its_account() {
        signInThroughGoogle(WEB, "code-1");
        UUID id = user("juan@gmail.com").id();
        String code = reconfirmWithGoogle(id, "code-2");

        assertThatThrownBy(() -> w.googleSignIn.confirmReauth(UUID.randomUUID(), code))
                .isInstanceOf(GoogleSignInFailedException.class);             // another account
        assertThatThrownBy(() -> w.googleSignIn.confirmReauth(id, code))
                .isInstanceOf(GoogleSignInFailedException.class);             // the code was spent by that attempt

        String second = reconfirmWithGoogle(id, "code-3");
        w.googleSignIn.confirmReauth(id, second);
        assertThatThrownBy(() -> w.googleSignIn.confirmReauth(id, second))
                .isInstanceOf(GoogleSignInFailedException.class);             // already used
    }

    @Test
    void re_confirming_with_another_google_account_is_refused() {
        signInThroughGoogle(WEB, "code-1");
        UUID id = user("juan@gmail.com").id();

        URI google = w.googleSignIn.startReauth(id, WEB);
        String state = google.getQuery().substring("state=".length());
        w.google.identity = new GoogleIdentity("google-sub-2", "otra@gmail.com", true, "Otra", "Persona");
        Map<String, String> back = query(w.googleSignIn.finish("code-2", state, null, TestWorld.CTX));

        assertThat(back).doesNotContainKey("google_reauth");
        assertThat(back).containsEntry("google_error", "auth.google_failed");
        assertThat(w.users.findById(id).orElseThrow().isDeleted()).isFalse();
    }
}
