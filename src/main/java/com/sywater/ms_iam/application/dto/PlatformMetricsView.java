package com.sywater.ms_iam.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * HU-062. A section is null when its service did not answer; its name is then in {@code unavailable}
 * (the users section always comes: it is the data of IAM itself).
 */
public record PlatformMetricsView(Instant generatedAt, Users users, Places places, Devices devices, List<String> unavailable) {

    /** active = verified and not blocked; inactive = blocked + unverified. */
    public record Users(long total, long active, long inactive, long blocked, long unverified) {
        public static Users of(UserCounts c) {
            return new Users(c.total(), c.active(), c.blocked() + c.unverified(), c.blocked(), c.unverified());
        }
    }

    /** active = the place has a linked device. */
    public record Places(long total, long active, long inactive) {}

    /** active = connected right now; linked = attached to a place. */
    public record Devices(long total, long active, long inactive, long linked) {}
}
