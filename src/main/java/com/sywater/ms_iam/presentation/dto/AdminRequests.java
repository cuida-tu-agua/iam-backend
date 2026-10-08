package com.sywater.ms_iam.presentation.dto;

import jakarta.validation.constraints.Size;

public final class AdminRequests {

    private AdminRequests() {
    }

    /** Optional note that goes to the audit log. */
    public record Reason(@Size(max = 200) String reason) {}
}
