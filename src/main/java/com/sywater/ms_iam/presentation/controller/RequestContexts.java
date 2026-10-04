package com.sywater.ms_iam.presentation.controller;

import com.sywater.ms_iam.application.dto.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

final class RequestContexts {

    private RequestContexts() {
    }

    static RequestContext from(HttpServletRequest request) {
        return new RequestContext(cut(request.getRemoteAddr(), 45), cut(request.getHeader(HttpHeaders.USER_AGENT), 500));
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
