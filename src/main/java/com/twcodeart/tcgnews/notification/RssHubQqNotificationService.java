package com.twcodeart.tcgnews.notification;

import com.twcodeart.tcgnews.news.NewsIngestionService;
import com.twcodeart.tcgnews.notification.qq.QqNotificationSender;
import com.twcodeart.tcgnews.source.ws.RssHubProperties;
import com.twcodeart.tcgnews.source.ws.WsRssHubNewsSource;
import com.twcodeart.tcgnews.source.ocg.OcgRssHubNewsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "rsshub", name = "enabled", havingValue = "true")
public class RssHubQqNotificationService {

    private static final Logger log = LoggerFactory.getLogger(RssHubQqNotificationService.class);
    private final SourceNotificationBootstrapRepository bootstraps;
    private final RssHubProperties properties;
    private final ObjectProvider<QqNotificationSender> senders;
    private final NewsNotificationService notifications;

    public RssHubQqNotificationService(SourceNotificationBootstrapRepository bootstraps, RssHubProperties properties,
                                       ObjectProvider<QqNotificationSender> senders, NewsNotificationService notifications) {
        this.bootstraps = bootstraps;
        this.properties = properties;
        this.senders = senders;
        this.notifications = notifications;
    }

    public boolean handles(String sourceId) {
        return WsRssHubNewsSource.SOURCE_ID.equals(sourceId) || OcgRssHubNewsSource.SOURCE_ID.equals(sourceId);
    }

    public int notificationLimit() { return properties.getNotificationBatchLimit(); }

    public void notifySavedItems(NewsIngestionService.IngestionResult result, boolean ingestionCompleted) {
        notifySavedItems(WsRssHubNewsSource.SOURCE_ID, result, ingestionCompleted, notificationLimit());
    }

    public int notifySavedItems(String sourceId, NewsIngestionService.IngestionResult result,
                                boolean ingestionCompleted, int availableMessages) {
        if (!handles(sourceId)) { throw new IllegalArgumentException("Unsupported RSSHub notification source"); }
        var saved = result.savedItems();
        try {
            if (!bootstraps.existsById(sourceId)) {
                // An empty or partially saved batch is not a complete history baseline.
                if (ingestionCompleted && result.fetched() > 0) {
                    bootstraps.saveAndFlush(new SourceNotificationBootstrap(sourceId));
                }
                log.info("RSSHub QQ bootstrap: source={}, saved={}, bootstrapSkipped={}, completed={}",
                        sourceId, saved.size(), saved.size(), ingestionCompleted && result.fetched() > 0);
                return 0;
            }
        } catch (RuntimeException exception) {
            // Fail closed if the baseline cannot be checked/stored; saved news remains intact.
            log.error("RSSHub QQ bootstrap state unavailable: source={}, notificationsSkipped={}",
                    sourceId, saved.size(), exception);
            return 0;
        }
        if (saved.isEmpty()) {
            return 0;
        }
        var sender = senders.getIfAvailable();
        if (sender == null) {
            log.info("RSSHub QQ sender disabled: source={}, notificationsSkipped={}", sourceId, saved.size());
            return 0;
        }
        int limit = Math.max(0, Math.min(properties.getNotificationBatchLimit(), availableMessages));
        if (OcgRssHubNewsSource.SOURCE_ID.equals(sourceId) || properties.isOcgEnabled() || sender.acceptsImages()) {
            int attempted = 0;
            int processed = 0;
            for (var batch : NewsForwardPackets.plan(saved, QqNotificationSender.MAX_FORWARD_ITEMS)) {
                if (attempted >= limit) break;
                var delivery = notifications.notifyBatch(batch, sender);
                attempted++;
                processed += batch.size();
                log.info("RSSHub forward packet: source={}, records={}, sentRecords={}, failedRecords={}", sourceId, batch.size(), delivery.sent(), delivery.failed());
            }
            log.info("RSSHub QQ packets: source={}, saved={}, packetsAttempted={}, recordsSkipped={}", sourceId, saved.size(), attempted, saved.size() - processed);
            return attempted;
        }
        var selected = saved.subList(0, Math.min(limit, saved.size()));
        var delivery = notifications.notifyNewItems(selected, sender);
        log.info("RSSHub QQ notifications: source={}, saved={}, attempted={}, sent={}, failed={}, limitSkipped={}",
                sourceId, saved.size(), selected.size(), delivery.sent(), delivery.failed(), saved.size() - selected.size());
        return selected.size();
    }
}
