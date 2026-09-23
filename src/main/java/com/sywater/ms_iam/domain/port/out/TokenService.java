//package com.sywater.ms_iam.domain.port.out;
//
//
//import com.sywater.ms_iam.domain.model.User;
//
//import java.time.Instant;
//import java.util.Set;
//import java.util.UUID;
//
//public interface TokenService {
//
//    String generateAccessToken(
//            UUID userUuid,
//            String email,
//            Set<String> roles
//    );
//
//    TokenClaims parseAndValidate(String token);
//
//    record TokenClaims(
//            UUID userUuid,
//            String email,
//            Set<String> roles,
//            String jti,
//            Instant issuedAt,
//            Instant expiresAt
//    ) {}
//}