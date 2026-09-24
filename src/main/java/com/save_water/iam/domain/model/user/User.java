package com.save_water.iam.domain.model.user;

import java.util.Set;

public class User {
    private final UserId id;
    private final String email;
    private final String passwordHash;
    private final Set<Role> roles;

    public User(UserId id, String email, String passwordHash, Set<Role> roles) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.roles = roles;
    }

    public UserId getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Set<Role> getRoles() { return roles; }
}