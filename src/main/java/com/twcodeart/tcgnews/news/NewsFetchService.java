package com.twcodeart.tcgnews.news;

import com.twcodeart.tcgnews.notification.NewsNotificationService;
import com.twcodeart.tcgnews.notification.RssHubQqNotificationService;
import org.springframework.beans.factory.ObjectProvider;
import com.twcodeart.tcgnews.source.NewsSource;
import com.twcodeart.tcgnews.source.NewsSourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NewsFetchService {

    private static final Logger log = LoggerFactory.getLogger(NewsFetchService.class);
    private final List<NewsSource> sources;
    private final NewsIngestionService ingestion;
    private final NewsNotificationService notifications;
    private final ObjectProvider<RssHubQqNotificationService> rssHubNotifications;

    public NewsFetchService(List<NewsSource> sources, NewsIngestionService ingestion,
                            NewsNotificationService notifications,
                            ObjectProvider<RssHubQqNotificationService> rssHubNotifications) {
        this.sources = List.copyOf(sources);
        this.ingestion = ingestion;
        this.notifications = notifications;
        this.rssHubNotifications = rssHubNotifications;
    }

    public NewsFetchSummary fetchAll() {
        return fetchSources(sources);
    }

    public NewsFetchSummary fetchSource(String sourceId) {
        var selected = sources.stream().filter(source -> sourceId.equals(source.getSourceId())).toList();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("No configured news source: " + sourceId);
        }
        return fetchSources(selected);
    }

    // Manual and scheduled fetches share this lock and the same save/notification flow.
    private synchronized NewsFetchSummary fetchSources(List<NewsSource> selectedSources) {
        int fetched = 0;
        int saved = 0;
        int duplicates = 0;
        int unprocessed = 0;
        int successfulSources = 0;
        List<NewsFetchSummary.SourceError> errors = new ArrayList<>();
        for (NewsSource source : selectedSources) {
            NewsIngestionService.IngestionResult result;
            boolean ingestionCompleted = false;
            try {
                result = ingestion.fetchAndSaveWithSummary(source);
                ingestionCompleted = true;
                successfulSources++;
            } catch (NewsIngestionService.IngestionFailure exception) {
                // Earlier saves have committed; retain their counts rather than
                // reporting zero or incorrectly treating remaining items as duplicates.
                result = exception.getResult();
                log.error("News persistence failed: source={}", source.getSourceId(), exception);
                errors.add(new NewsFetchSummary.SourceError(source.getSourceId(), "News persistence failed"));
            } catch (NewsSourceException | RuntimeException exception) {
                log.error("News source failed: source={}", source.getSourceId(), exception);
                errors.add(new NewsFetchSummary.SourceError(source.getSourceId(), "Source fetch failed"));
                continue;
            }
            fetched += result.fetched();
            saved += result.savedItems().size();
            duplicates += result.duplicates();
            unprocessed += result.unprocessed();
            // Persisted records are available even when the source's batch saved only partially.
            var rssHub = rssHubNotifications.getIfAvailable();
            if (rssHub != null && rssHub.handles(source.getSourceId())) {
                rssHub.notifySavedItems(result, ingestionCompleted);
            } else {
                notifications.notifyNewItems(result.savedItems());
            }
        }
        return new NewsFetchSummary(fetched, saved, duplicates, unprocessed, successfulSources, errors);
    }
}
