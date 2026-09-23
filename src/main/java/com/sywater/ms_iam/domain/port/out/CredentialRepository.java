//package com.sywater.ms_iam.domain.port.out;
//
//import java.util.Optional;
//
//public interface CredentialRepository {
//    Optional<String> findPasswordHash(Long userId);
//    Optional<Long> findUserIdByGoogleId(String googleId);
//    void saveLocal(Long userId, String passwordHash);         // inserta o actualiza
//    void saveGoogle(Long userId, String googleId);
//}