package com.sywater.ms_iam.domain.model;

import java.time.Instant;
import java.util.UUID;

public class User {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private boolean emailVerified;
    private boolean accountLocked;
    private Instant createdAt;
    private Instant updatedAt;

    public User(String email, String firstName, String lastName) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.emailVerified = false;
        this.accountLocked = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhone() { return phone; }
    public boolean isEmailVerified() { return emailVerified; }
    public boolean isAccountLocked() { return accountLocked; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Setters
    public void setPhone(String phone) { this.phone = phone; }
    public void setEmailVerified(boolean verified) { this.emailVerified = verified; }
}