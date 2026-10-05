package com.twcodeart.tcgnews.news;

import java.util.List;

public record NewsFetchSummary(int fetched, int saved, int duplicates, int unprocessed,
                               int successfulSources, List<SourceError> errors) {

    public NewsFetchSummary {
        errors = List.copyOf(errors);
    }

    public boolean allSourcesFailed() {
        return successfulSources == 0 && !errors.isEmpty();
    }

    public record SourceError(String sourceId, String message) {
    }
}
