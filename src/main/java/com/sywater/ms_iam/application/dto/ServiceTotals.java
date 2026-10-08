package com.sywater.ms_iam.application.dto;

/** What the other services report for HU-062. */
public final class ServiceTotals {

    private ServiceTotals() {}

    /** ms-places: places that are not deleted. */
    public record Places(long total) {}

    /** ms-device: devices that exist, those connected right now and those linked to a place. */
    public record Devices(long total, long connected, long linked) {}
}
