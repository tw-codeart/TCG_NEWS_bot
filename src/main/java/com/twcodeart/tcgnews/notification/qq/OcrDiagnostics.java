package com.twcodeart.tcgnews.notification.qq;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/** Process-local observations; no card text, image URLs, paths or credentials are exposed. */
@org.springframework.stereotype.Component
public final class OcrDiagnostics {
    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private volatile Snapshot snapshot = new Snapshot(null, null, null, 0, 0);
    public synchronized void record(String provider, String reason) {
        if ("SUCCESS".equals(reason)) successes.incrementAndGet(); else failures.incrementAndGet();
        snapshot = new Snapshot(Instant.now(), provider, reason, successes.get(), failures.get());
    }
    public Snapshot snapshot() { return snapshot; }
    public record Snapshot(Instant lastAttemptAt, String provider, String result, long successes, long failures) { }
}
