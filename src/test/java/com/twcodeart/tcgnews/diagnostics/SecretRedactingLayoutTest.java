package com.twcodeart.tcgnews.diagnostics;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretRedactingLayoutTest {
    @Test
    void masksHeadersAndCredentialFields() {
        String rendered = new SecretRedactingLayout().redact("Authorization: Bearer sample-secret\n"
                + "Cookie: auth_token=sample-cookie; other=sample-value\n"
                + "QQ_ACCESS_TOKEN=sample-qq\nTWITTER_AUTH_TOKEN=sample-x\n"
                + "QQ_COMMAND_SECRET=sample-command\n"
                + "{\"access_token\":\"sample-json\",\"password\":\"sample-password\"}\n"
                + "http://localhost/path?token=sample-url&other=kept");
        assertThat(rendered).doesNotContain("sample-secret", "sample-cookie", "sample-value",
                "sample-qq", "sample-x", "sample-command", "sample-json", "sample-password", "sample-url")
                .contains("[REDACTED]", "other=kept");
    }

    @Test
    void masksExceptionTracesAndPreservesJapaneseTextAndOrdinaryUrls() {
        var context = new LoggerContext();
        try {
            var layout = new SecretRedactingLayout();
            layout.setContext(context); layout.setPattern("%msg%n%ex"); layout.start();
            var event = new LoggingEvent();
            event.setMessage("今日のカード https://x.com/wstcg/status/123");
            event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("auth_token=sample-trace")));
            assertThat(layout.doLayout(event)).contains("今日のカード", "https://x.com/wstcg/status/123",
                    "IllegalStateException", "[REDACTED]").doesNotContain("sample-trace");
        } finally { context.stop(); }
    }
}
