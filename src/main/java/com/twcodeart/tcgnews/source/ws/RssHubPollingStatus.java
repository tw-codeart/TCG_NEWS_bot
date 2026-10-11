package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.NewsFetchSummary;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class RssHubPollingStatus {
    private volatile Snapshot snapshot = new Snapshot(null, null, null, 0, 0, 0, 0, null);

    public void started() { snapshot = new Snapshot(Instant.now(), snapshot.lastCompletedAt(), null, 0, 0, 0, 0, null); }
    public void completed(NewsFetchSummary result, String errorId) {
        snapshot = new Snapshot(snapshot.lastStartedAt(), Instant.now(), result.errors().isEmpty(),
                result.fetched(), result.saved(), result.duplicates(), result.errors().size(), errorId);
    }
    public void failed(String errorId) {
        snapshot = new Snapshot(snapshot.lastStartedAt(), Instant.now(), false, 0, 0, 0, 1, errorId);
    }
    public Snapshot snapshot() { return snapshot; }
    public record Snapshot(Instant lastStartedAt, Instant lastCompletedAt, Boolean lastFetchSuccessful,
                           int fetched, int saved, int duplicates, int sourceErrors, String errorId) { }
}
