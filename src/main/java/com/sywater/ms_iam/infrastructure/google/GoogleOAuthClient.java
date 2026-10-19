package com.sywater.ms_iam.infrastructure.google;

import com.sywater.ms_iam.application.port.out.GoogleIdentityProvider;
import com.sywater.ms_iam.domain.exception.ExternalServiceUnavailableException;
import com.sywater.ms_iam.domain.exception.GoogleSignInDisabledException;
import com.sywater.ms_iam.domain.exception.GoogleSignInFailedException;
import com.sywater.ms_iam.infrastructure.config.GoogleOAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HU-004: Google as OpenID Connect provider.
 * <ul>
 *   <li>Authorization endpoint with scope "openid email profile", state and PKCE (S256).</li>
 *   <li>Token endpoint called server to server with the client secret.</li>
 *   <li>The ID token is checked like any foreign JWT: RS256 signature against Google's published keys (JWKS),
 *       issuer, audience = our client id, and expiry.</li>
 * </ul>
 */
@Component
public class GoogleOAuthClient implements GoogleIdentityProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClient.class);

    static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final GoogleOAuthProperties properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private volatile JwtDecoder idTokenDecoder;

    public GoogleOAuthClient(GoogleOAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean configured() {
        return properties.configured();
    }

    @Override
    public URI authorizationUrl(String state, String codeVerifier) {
        if (!configured()) throw new GoogleSignInDisabledException();
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", properties.clientId());
        query.put("redirect_uri", properties.redirectUri());
        query.put("response_type", "code");
        query.put("scope", "openid email profile");
        query.put("state", state);
        query.put("code_challenge", codeChallenge(codeVerifier));
        query.put("code_challenge_method", "S256");
        query.put("prompt", "select_account");   // lets the person choose which Google account to use
        return URI.create(AUTHORIZATION_ENDPOINT + "?" + form(query));
    }

    @Override
    public GoogleIdentity identify(String authorizationCode, String codeVerifier) {
        if (!configured()) throw new GoogleSignInDisabledException();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", authorizationCode);
        body.put("client_id", properties.clientId());
        body.put("client_secret", properties.clientSecret());
        body.put("redirect_uri", properties.redirectUri());
        body.put("grant_type", "authorization_code");
        body.put("code_verifier", codeVerifier);

        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_ENDPOINT))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form(body)))
                .build();

        String idToken;
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // Only the error code: the body never carries tokens when the exchange fails
                log.warn("Google rejected the authorization code: HTTP {} {}", response.statusCode(),
                        errorCode(response.body()));
                throw new GoogleSignInFailedException("Google did not accept the sign-in. Try again.");
            }
            Object value = json.readValue(response.body(), Map.class).get("id_token");
            idToken = value == null ? null : value.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceUnavailableException("Google");
        } catch (GoogleSignInFailedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Google's token endpoint could not be reached: {}", e.toString());
            throw new ExternalServiceUnavailableException("Google");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new GoogleSignInFailedException("Google did not send the identity of the account.");
        }

        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException e) {
            log.warn("Google's ID token was rejected: {}", e.getMessage());
            throw new GoogleSignInFailedException("The answer from Google could not be verified. Try again.");
        }
        return new GoogleIdentity(jwt.getSubject(), jwt.getClaimAsString("email"), isTrue(jwt.getClaim("email_verified")),
                jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"));
    }

    private JwtDecoder decoder() {
        JwtDecoder decoder = idTokenDecoder;
        if (decoder == null) {
            synchronized (this) {
                if (idTokenDecoder == null) {
                    NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(JWKS_URI)
                            .jwsAlgorithm(SignatureAlgorithm.RS256)
                            .build();
                    String clientId = properties.clientId();
                    nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                            new JwtTimestampValidator(Duration.ofSeconds(60)),
                            new JwtClaimValidator<Object>("iss", iss -> iss != null && ISSUERS.contains(iss.toString())),
                            new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(clientId))));
                    idTokenDecoder = nimbus;
                }
                decoder = idTokenDecoder;
            }
        }
        return decoder;
    }

    static String codeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static boolean isTrue(Object claim) {
        return claim instanceof Boolean b ? b : claim != null && "true".equalsIgnoreCase(claim.toString());
    }

    private String errorCode(String body) {
        try {
            Object error = json.readValue(body, Map.class).get("error");
            return error == null ? "" : error.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
