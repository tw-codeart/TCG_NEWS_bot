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
    private final com.twcodeart.tcgnews.subscription.QqGroupDeliveryService groups;

    public NewsFetchService(List<NewsSource> sources, NewsIngestionService ingestion,
                            NewsNotificationService notifications,
                            ObjectProvider<RssHubQqNotificationService> rssHubNotifications) {
        this.sources = List.copyOf(sources);
        this.ingestion = ingestion;
        this.notifications = notifications;
        this.rssHubNotifications = rssHubNotifications;
        this.groups=null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NewsFetchService(List<NewsSource> sources,NewsIngestionService ingestion,NewsNotificationService notifications,
            ObjectProvider<RssHubQqNotificationService> rssHubNotifications,
            ObjectProvider<com.twcodeart.tcgnews.subscription.QqGroupDeliveryService> groups){
        this.sources=List.copyOf(sources);this.ingestion=ingestion;this.notifications=notifications;
        this.rssHubNotifications=rssHubNotifications;this.groups=groups.getIfAvailable();
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

    public NewsFetchSummary fetchSourcesByIds(List<String> sourceIds) {
        var selected = sources.stream().filter(source -> sourceIds.contains(source.getSourceId())).toList();
        if (selected.stream().map(NewsSource::getSourceId).distinct().count() != sourceIds.stream().distinct().count()) {
            throw new IllegalArgumentException("A requested news source is not configured");
        }
        return fetchSources(selected);
    }

    /** Shares the normal fetch lock and ingestion policy, but never calls a sender. */
    public synchronized List<BackfillSource> refreshForBackfill(Game game) {
        var rssHub = rssHubNotifications.getIfAvailable();
        var selected = sources.stream().filter(source -> source.getGame() == game
                && com.twcodeart.tcgnews.notification.RssHubNotificationSources.supports(source.getSourceId())).toList();
        if (rssHub == null || selected.isEmpty()) throw new IllegalArgumentException(game == Game.YUGIOH_OCG
                ? "Enable RSSHUB_OCG_ENABLED=true in config.env and restart collection"
                : "Enable the WS RSSHub source and restart collection");
        var reports = new ArrayList<BackfillSource>();
        for (NewsSource source : selected) {
            try {
                rssHub.prepareSource(source.getSourceId());
                if (groups != null) groups.prepare(source.getSourceId());
                var snapshot = List.copyOf(source.fetchNews());
                var result = ingestion.fetchAndSaveWithSummary(new NewsSource() {
                    public String getSourceId() { return source.getSourceId(); }
                    public Game getGame() { return source.getGame(); }
                    public List<NewsItem> fetchNews() { return snapshot; }
                });
                if (groups != null) groups.capture(source.getSourceId(), result, true);
                else rssHub.captureSource(source.getSourceId(), result.fetched() > 0);
                var dates = snapshot.stream().map(NewsItem::getPublishedAt).filter(java.util.Objects::nonNull).sorted().toList();
                reports.add(new BackfillSource(source.getSourceId(), result.fetched(), result.savedItems().size(),
                        dates.isEmpty() ? null : dates.get(0), dates.isEmpty() ? null : dates.get(dates.size()-1), null));
            } catch (com.twcodeart.tcgnews.source.NewsSourceException | RuntimeException exception) {
                String id = com.twcodeart.tcgnews.diagnostics.DiagnosticIds.current();
                log.error("Backfill refresh failed; other sources continue: errorId={}, source={}", id, source.getSourceId(), exception);
                reports.add(new BackfillSource(source.getSourceId(), 0, 0, null, null, id));
            }
        }
        return List.copyOf(reports);
    }

    public record BackfillSource(String sourceId, int fetched, int saved, java.time.Instant oldestAvailable,
                                 java.time.Instant newestAvailable, String errorId) { }

    // Manual and scheduled fetches share this lock and the same save/notification flow.
    private synchronized NewsFetchSummary fetchSources(List<NewsSource> selectedSources) {
        var rssHub = rssHubNotifications.getIfAvailable();
        int remainingMessages = rssHub == null ? 0 : rssHub.notificationLimit();
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
                if (rssHub != null && rssHub.handles(source.getSourceId())) rssHub.prepareSource(source.getSourceId());
                if (groups != null && rssHub != null && rssHub.handles(source.getSourceId())) groups.prepare(source.getSourceId());
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
                if (rssHub != null && rssHub.handles(source.getSourceId())) {
                    if(groups!=null)groups.capture(source.getSourceId(),new NewsIngestionService.IngestionResult(0,List.of(),0),false);
                    else remainingMessages -= rssHub.notifySavedItems(source.getSourceId(),
                            new NewsIngestionService.IngestionResult(0, List.of(), 0), false, remainingMessages);
                }
                continue;
            }
            fetched += result.fetched();
            saved += result.savedItems().size();
            duplicates += result.duplicates();
            unprocessed += result.unprocessed();
            // Persisted records are available even when the source's batch saved only partially.
            if (rssHub != null && rssHub.handles(source.getSourceId())) {
                if(groups!=null)groups.capture(source.getSourceId(),result,ingestionCompleted);
                else remainingMessages -= rssHub.notifySavedItems(source.getSourceId(), result, ingestionCompleted, remainingMessages);
            } else {
                notifications.notifyNewItems(result.savedItems());
            }
        }
        if(groups!=null&&rssHub!=null){
            try{groups.deliver(selectedSources.stream().map(NewsSource::getSourceId).filter(rssHub::handles).toList(),rssHub.notificationLimit());}
            catch(RuntimeException exception){log.error("Group delivery stopped; saved news retained: errorId={}",com.twcodeart.tcgnews.diagnostics.DiagnosticIds.current(),exception);}
        }
        return new NewsFetchSummary(fetched, saved, duplicates, unprocessed, successfulSources, errors);
    }
}
