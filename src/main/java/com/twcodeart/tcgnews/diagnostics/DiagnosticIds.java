package com.twcodeart.tcgnews.diagnostics;

import org.slf4j.MDC;

import java.util.UUID;

public final class DiagnosticIds {
    public static final String MDC_KEY = "requestId";

    private DiagnosticIds() { }

    public static String current() {
        String id = MDC.get(MDC_KEY);
        return id == null ? UUID.randomUUID().toString() : id;
    }
}
