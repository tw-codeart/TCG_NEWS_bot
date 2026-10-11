package com.twcodeart.tcgnews.diagnostics;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.List;
import java.util.regex.Pattern;

/** Redacts rendered messages and exception traces before any appender writes them. */
public class SecretRedactingLayout extends PatternLayout {
    private static final Pattern HEADERS = Pattern.compile(
            "(?im)(\\b(?:authorization|cookie|set-cookie)\\s*[:=]\\s*)[^\\r\\n]+");
    private static final Pattern KEYS = Pattern.compile(
            "(?i)(\\b(?:auth_token|access_token|qq_access_token|twitter_auth_token|qq_command_secret|command[-_]?secret|password|client_secret|token)"
                    + "[\\\"']?\\s*[:=]\\s*[\\\"']?)[^\\s\\\"'&,;}]+"
    );
    private final List<String> configuredSecrets = java.util.stream.Stream.of(
                    "QQ_ACCESS_TOKEN", "TWITTER_AUTH_TOKEN", "QQ_COMMAND_SECRET")
            .map(System::getenv).filter(value -> value != null && !value.isBlank()).toList();

    @Override
    public String doLayout(ILoggingEvent event) {
        return redact(super.doLayout(event));
    }

    public String redact(String text) {
        for (String secret : configuredSecrets) text = text.replace(secret, "[REDACTED]");
        text = HEADERS.matcher(text).replaceAll("$1[REDACTED]");
        return KEYS.matcher(text).replaceAll("$1[REDACTED]");
    }
}
