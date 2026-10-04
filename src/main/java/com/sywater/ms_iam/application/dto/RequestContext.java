package com.sywater.ms_iam.application.dto;

public record RequestContext(String ip, String userAgent) {

    public static final RequestContext UNKNOWN = new RequestContext("unknown", null);

    public RequestContext {
        ip = ip == null || ip.isBlank() ? "unknown" : truncate(ip, 45);
        userAgent = userAgent == null ? null : truncate(userAgent, 500);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
