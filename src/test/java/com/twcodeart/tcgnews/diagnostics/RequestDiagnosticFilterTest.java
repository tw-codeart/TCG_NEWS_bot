package com.twcodeart.tcgnews.diagnostics;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RequestDiagnosticFilterTest {
    @Test
    void suppliesServerGeneratedIdAndCleansUpThreadState() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "untrusted");
        var response = new MockHttpServletResponse();
        new RequestDiagnosticFilter().doFilter(request, response, (req, res) -> {
            assertThat(DiagnosticIds.current()).isEqualTo(response.getHeader("X-Request-ID"));
            assertThat(UUID.fromString(DiagnosticIds.current())).isNotNull();
        });
        assertThat(response.getHeader("X-Request-ID")).isNotEqualTo("untrusted");
        assertThat(MDC.get(DiagnosticIds.MDC_KEY)).isNull();
    }

    @Test
    void restoresExistingContextEvenWhenRequestFails() {
        MDC.put(DiagnosticIds.MDC_KEY, "outer");
        try {
            assertThatThrownBy(() -> new RequestDiagnosticFilter().doFilter(
                    new MockHttpServletRequest(), new MockHttpServletResponse(),
                    (req, res) -> { throw new ServletException("failed"); })).isInstanceOf(ServletException.class);
            assertThat(MDC.get(DiagnosticIds.MDC_KEY)).isEqualTo("outer");
        } finally { MDC.remove(DiagnosticIds.MDC_KEY); }
    }
}
