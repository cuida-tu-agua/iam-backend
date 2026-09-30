package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.AuthResult;
import com.sywater.ms_iam.application.dto.AuthSettings;
import com.sywater.ms_iam.application.dto.RequestContext;
import com.sywater.ms_iam.application.dto.UserView;
import com.sywater.ms_iam.application.port.out.AccessTokenIssuer;
import com.sywater.ms_iam.application.port.out.RefreshTokenRepository;
import com.sywater.ms_iam.application.port.out.SecretGenerator;
import com.sywater.ms_iam.application.port.out.SecretHasher;
import com.sywater.ms_iam.domain.model.User;

import java.time.Instant;

public class SessionIssuer {

    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenRepository refreshTokens;
    private final SecretGenerator generator;
    private final SecretHasher hasher;
    private final AuthSettings settings;

    public SessionIssuer(AccessTokenIssuer accessTokens, RefreshTokenRepository refreshTokens,
                         SecretGenerator generator, SecretHasher hasher, AuthSettings settings) {
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.generator = generator;
        this.hasher = hasher;
        this.settings = settings;
    }

    public AuthResult open(User user, RequestContext context, Instant now) {
        AccessTokenIssuer.AccessToken access = accessTokens.issue(user, now);

        String refresh = generator.opaqueToken();
        Instant refreshExpiresAt = now.plus(settings.refreshTokenTtl());
        refreshTokens.save(user.id(), hasher.hash(refresh), context.userAgent(), refreshExpiresAt, now);

        return new AuthResult(access.value(), access.expiresAt(), refresh, refreshExpiresAt, UserView.from(user));
    }
}
