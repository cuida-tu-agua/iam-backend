package com.sywater.ms_iam.domain.exception;

import java.util.List;

public class WeakPasswordException extends DomainException {

    private final List<String> unmetRules;

    public WeakPasswordException(List<String> unmetRules) {
        super("user.weak_password", "The password does not meet the policy: " + String.join(", ", unmetRules));
        this.unmetRules = List.copyOf(unmetRules);
    }

    public List<String> unmetRules() {
        return unmetRules;
    }
}
