package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.AuthResult;
import com.sywater.ms_iam.application.dto.RequestContext;
import com.sywater.ms_iam.application.port.in.GoogleSignInUseCase;
import com.sywater.ms_iam.application.port.out.ActivityLog;
import com.sywater.ms_iam.application.port.out.CredentialRepository;
import com.sywater.ms_iam.application.port.out.GoogleIdentityProvider;
import com.sywater.ms_iam.application.port.out.GoogleIdentityProvider.GoogleIdentity;
import com.sywater.ms_iam.application.port.out.GoogleLoginStore;
import com.sywater.ms_iam.application.port.out.GoogleLoginStore.PendingAttempt;
import com.sywater.ms_iam.application.port.out.LoginAttemptRepository;
import com.sywater.ms_iam.application.port.out.SecretGenerator;
import com.sywater.ms_iam.application.port.out.SecretHasher;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.AccountBlockedException;
import com.sywater.ms_iam.domain.exception.DomainException;
import com.sywater.ms_iam.domain.exception.GoogleAccountConflictException;
import com.sywater.ms_iam.domain.exception.GoogleSignInCancelledException;
import com.sywater.ms_iam.domain.exception.GoogleSignInDisabledException;
import com.sywater.ms_iam.domain.exception.GoogleSignInFailedException;
import com.sywater.ms_iam.domain.exception.InvalidRedirectException;
import com.sywater.ms_iam.domain.exception.UserNotFoundException;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * HU-004 "Continuar con Google" with the OAuth 2.0 authorization code flow + PKCE. ms-iam is the confidential client:
 * the client secret and Google's tokens never reach the browser or the phone.
 * <ol>
 *   <li>{@link #start}: the app opens GET /api/auth/google/start?redirect=... → Google's consent page.</li>
 *   <li>{@link #finish}: Google returns to /api/auth/google/callback with the code; the account is found by its
 *       Google id, linked by e-mail or created, and the app address is opened with a single-use "google_code".</li>
 *   <li>{@link #exchange}: the app trades that code for the same session as a normal login.</li>
 * </ol>
 * Rules of HU-004: an e-mail that already has a local account gets BOTH methods linked (and becomes verified);
 * a new account created by Google needs no e-mail verification.
 */
public class GoogleSignInService implements GoogleSignInUseCase {

    static final Duration ATTEMPT_TTL = Duration.ofMinutes(10);
    static final Duration HANDOFF_TTL = Duration.ofMinutes(2);
    /** Keeps the re-confirmation codes apart from the sign-in codes in the store. */
    static final String REAUTH_PREFIX = "reauth:";
    static final String FALLBACK_FIRST_NAME = "Usuario";
    static final String FALLBACK_LAST_NAME = "Sin apellido";

    private final UserRepository users;
    private final CredentialRepository credentials;
    private final GoogleIdentityProvider google;
    private final GoogleLoginStore store;
    private final LoginAttemptRepository attempts;
    private final SessionIssuer sessions;
    private final ActivityLog activity;
    private final SecretGenerator generator;
    private final SecretHasher hasher;
    private final List<String> allowedRedirects;
    private final Clock clock;

    public GoogleSignInService(UserRepository users, CredentialRepository credentials, GoogleIdentityProvider google,
                               GoogleLoginStore store, LoginAttemptRepository attempts, SessionIssuer sessions,
                               ActivityLog activity, SecretGenerator generator, SecretHasher hasher,
                               List<String> allowedRedirects, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.google = google;
        this.store = store;
        this.attempts = attempts;
        this.sessions = sessions;
        this.activity = activity;
        this.generator = generator;
        this.hasher = hasher;
        this.allowedRedirects = allowedRedirects == null ? List.of()
                : allowedRedirects.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.clock = clock;
    }

    @Override
    public boolean enabled() {
        return google.configured();
    }

    @Override
    public URI start(String appRedirect) {
        return startAttempt(appRedirect, null);
    }

    @Override
    public URI startReauth(UUID userId, String appRedirect) {
        if (!google.configured()) throw new GoogleSignInDisabledException();
        users.findById(userId).filter(u -> !u.isDeleted()).orElseThrow(UserNotFoundException::new);
        if (credentials.findGoogleId(userId).isEmpty()) {
            throw new GoogleSignInFailedException("This account has no Google account linked.");
        }
        return startAttempt(appRedirect, userId);
    }

    private URI startAttempt(String appRedirect, UUID reauthUserId) {
        if (!google.configured()) throw new GoogleSignInDisabledException();
        URI redirect = allowedRedirect(appRedirect);

        String state = generator.opaqueToken();
        String codeVerifier = generator.opaqueToken();   // 43 URL-safe characters: a valid PKCE verifier
        store.saveAttempt(state, new PendingAttempt(redirect.toString(), codeVerifier, reauthUserId), ATTEMPT_TTL);
        return google.authorizationUrl(state, codeVerifier);
    }

    @Override
    @Transactional(noRollbackFor = DomainException.class)
    public URI finish(String authorizationCode, String state, String error, RequestContext context) {
        if (state == null || state.isBlank()) throw expired();
        PendingAttempt attempt = store.takeAttempt(state).orElseThrow(GoogleSignInService::expired);
        String appRedirect = attempt.appRedirect();

        try {
            if (error != null || authorizationCode == null || authorizationCode.isBlank()) {
                throw new GoogleSignInCancelledException();
            }
            GoogleIdentity identity = google.identify(authorizationCode, attempt.codeVerifier());
            if (attempt.reauthUserId() != null) return confirmedForDeletion(attempt, identity);

            User user = findLinkOrCreate(identity, context);
            if (user.isBlocked()) {
                attempts.record(user.id(), user.email().value(), context.ip(), false, "ACCOUNT_BLOCKED", clock.instant());
                throw new AccountBlockedException();
            }

            String handoff = generator.opaqueToken();
            store.saveHandoff(hasher.hash(handoff), user.id(), HANDOFF_TTL);
            return withParameter(appRedirect, "google_code", handoff);
        } catch (DomainException e) {
            return withParameter(appRedirect, "google_error", e.code());
        }
    }

    @Override
    public void confirmReauth(UUID userId, String reauthCode) {
        if (reauthCode == null || reauthCode.isBlank()) throw expired();
        UUID owner = store.takeHandoff(hasher.hash(REAUTH_PREFIX + reauthCode)).orElseThrow(GoogleSignInService::expired);
        if (!owner.equals(userId)) throw expired();
    }

    @Override
    @Transactional
    public AuthResult exchange(String handoffCode, RequestContext context) {
        Instant now = clock.instant();
        if (handoffCode == null || handoffCode.isBlank()) throw expired();

        UUID userId = store.takeHandoff(hasher.hash(handoffCode)).orElseThrow(GoogleSignInService::expired);
        User user = users.findById(userId).filter(u -> !u.isDeleted()).orElseThrow(GoogleSignInService::expired);
        if (user.isBlocked()) throw new AccountBlockedException();

        attempts.record(user.id(), user.email().value(), context.ip(), true, null, now);
        activity.record(user.id(), "LOGIN_GOOGLE", Map.of(), context.ip(), now);
        return sessions.open(user, context, now);
    }

    /** The Google account that comes back must be the one linked to the account that asked to re-confirm. */
    private URI confirmedForDeletion(PendingAttempt attempt, GoogleIdentity identity) {
        Optional<String> linked = credentials.findGoogleId(attempt.reauthUserId());
        if (linked.isEmpty() || !linked.get().equals(identity.subject())) {
            throw new GoogleSignInFailedException("The Google account does not belong to this account.");
        }
        String code = generator.opaqueToken();
        store.saveHandoff(hasher.hash(REAUTH_PREFIX + code), attempt.reauthUserId(), HANDOFF_TTL);
        return withParameter(attempt.appRedirect(), "google_reauth", code);
    }

    /** HU-004: Google id already linked → that account; same e-mail → link both methods; otherwise a new account. */
    private User findLinkOrCreate(GoogleIdentity identity, RequestContext context) {
        Instant now = clock.instant();
        if (identity.subject() == null || identity.subject().isBlank() || identity.email() == null) {
            throw new GoogleSignInFailedException("Google did not send the account data.");
        }
        if (!identity.emailVerified()) {
            throw new GoogleSignInFailedException("The e-mail of this Google account is not verified.");
        }

        Optional<UUID> linked = credentials.findUserIdByGoogleId(identity.subject());
        if (linked.isPresent()) {
            Optional<User> owner = users.findById(linked.get()).filter(u -> !u.isDeleted());
            if (owner.isPresent()) return owner.get();
            credentials.removeGoogleId(linked.get());   // the account was deleted (HU-008): the Google id is free again
        }

        Email email = new Email(identity.email());
        Optional<User> sameEmail = users.findByEmail(email).filter(u -> !u.isDeleted());
        if (sameEmail.isPresent()) {
            User user = sameEmail.get();
            Optional<String> otherGoogle = credentials.findGoogleId(user.id());
            if (otherGoogle.isPresent() && !otherGoogle.get().equals(identity.subject())) {
                throw new GoogleAccountConflictException();
            }
            credentials.saveGoogleId(user.id(), identity.subject());
            if (!user.isEmailVerified()) {
                user.verifyEmailThroughGoogle(now);
                users.update(user);
            }
            activity.record(user.id(), "GOOGLE_LINKED", Map.of(), context.ip(), now);
            return user;
        }

        User user = User.registerWithGoogle(name(identity.givenName(), localPart(email)),
                name(identity.familyName(), FALLBACK_LAST_NAME), email, now);
        users.create(user);
        credentials.saveGoogleId(user.id(), identity.subject());
        activity.record(user.id(), "REGISTERED_GOOGLE", Map.of(), context.ip(), now);
        return user;
    }

    /**
     * Only the app addresses of the configuration. "https://sy-water.tech" allows that exact origin (any path);
     * an entry that ends in ":" ("saveyourwater:", "exp:") allows the whole scheme of the mobile app.
     */
    URI allowedRedirect(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 500) throw new InvalidRedirectException();
        URI uri;
        try {
            uri = new URI(raw.trim());
        } catch (Exception e) {
            throw new InvalidRedirectException();
        }
        if (uri.getScheme() == null || uri.getRawFragment() != null) throw new InvalidRedirectException();
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);

        for (String allowed : allowedRedirects) {
            if (allowed.endsWith(":")) {
                if (scheme.equals(allowed.substring(0, allowed.length() - 1).toLowerCase(Locale.ROOT))) return uri;
                continue;
            }
            URI origin = URI.create(allowed);
            if (origin.getScheme() == null || uri.getHost() == null) continue;
            if (scheme.equals(origin.getScheme().toLowerCase(Locale.ROOT))
                    && uri.getHost().equalsIgnoreCase(origin.getHost())
                    && port(uri) == port(origin)) {
                return uri;
            }
        }
        throw new InvalidRedirectException();
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : "http".equalsIgnoreCase(uri.getScheme()) ? 80 : -1;
    }

    private static URI withParameter(String base, String name, String value) {
        String separator = base.contains("?") ? "&" : "?";
        return URI.create(base + separator + name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    /** Google names can bring digits, emojis... The account keeps only what the name rules accept. */
    static String name(String raw, String fallback) {
        if (raw == null) return fallback;
        String clean = raw.replaceAll("[^\\p{L} '.-]", "")
                .replaceAll("^[^\\p{L}]+", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (clean.length() > User.NAME_MAX) clean = clean.substring(0, User.NAME_MAX).trim();
        return clean.length() >= User.NAME_MIN ? clean : fallback;
    }

    private static String localPart(Email email) {
        String value = email.value();
        int at = value.indexOf('@');
        String local = at > 0 ? value.substring(0, at) : value;
        String clean = name(local.replaceAll("[._-]+", " "), FALLBACK_FIRST_NAME);
        return clean.isEmpty() ? FALLBACK_FIRST_NAME : Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    private static GoogleSignInFailedException expired() {
        return new GoogleSignInFailedException("The sign-in with Google expired or was already used. Try again.");
    }
}
