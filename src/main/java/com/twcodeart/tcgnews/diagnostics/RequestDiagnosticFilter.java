package com.twcodeart.tcgnews.diagnostics;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestDiagnosticFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        String previous = MDC.get(DiagnosticIds.MDC_KEY);
        String id = UUID.randomUUID().toString();
        MDC.put(DiagnosticIds.MDC_KEY, id);
        response.setHeader("X-Request-ID", id);
        try {
            chain.doFilter(request, response);
        } finally {
            if (previous == null) MDC.remove(DiagnosticIds.MDC_KEY);
            else MDC.put(DiagnosticIds.MDC_KEY, previous);
        }
    }
}
