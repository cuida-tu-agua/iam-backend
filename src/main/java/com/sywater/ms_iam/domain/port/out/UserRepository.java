//package com.sywater.ms_iam.domain.port.out;
//
//import com.sywater.ms_iam.domain.model.Email;
//import com.sywater.ms_iam.domain.model.User;
//
//import java.util.Optional;
//import java.util.UUID;
//
//public interface UserRepository {
//    Optional<User> findByEmail(Email email);
//    Optional<User> findByUuid(UUID uuid);
//    Optional<User> findByPhone(String phone);
//    boolean existsByEmail(Email email);
//    User save(User user);                                    // devuelve con id asignado
//}