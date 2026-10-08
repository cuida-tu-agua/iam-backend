package com.sywater.ms_iam.application.dto;

/** HU-062: accounts that are not deleted, split by what an administrator sees (see AccountStatus). */
public record UserCounts(long active, long blocked, long unverified) {

    public long total() {
        return active + blocked + unverified;
    }
}
