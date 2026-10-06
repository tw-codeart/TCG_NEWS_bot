package com.twcodeart.tcgnews.notification;

import com.twcodeart.tcgnews.news.NewsIngestionService;
import com.twcodeart.tcgnews.notification.qq.QqNotificationSender;
import com.twcodeart.tcgnews.source.ws.RssHubProperties;
import com.twcodeart.tcgnews.source.ws.WsRssHubNewsSource;
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
        return WsRssHubNewsSource.SOURCE_ID.equals(sourceId);
    }

    public void notifySavedItems(NewsIngestionService.IngestionResult result, boolean ingestionCompleted) {
        String sourceId = WsRssHubNewsSource.SOURCE_ID;
        var saved = result.savedItems();
        try {
            if (!bootstraps.existsById(sourceId)) {
                // An empty or partially saved batch is not a complete history baseline.
                if (ingestionCompleted && result.fetched() > 0) {
                    bootstraps.saveAndFlush(new SourceNotificationBootstrap(sourceId));
                }
                log.info("RSSHub QQ bootstrap: source={}, saved={}, bootstrapSkipped={}, completed={}",
                        sourceId, saved.size(), saved.size(), ingestionCompleted && result.fetched() > 0);
                return;
            }
        } catch (RuntimeException exception) {
            // Fail closed if the baseline cannot be checked/stored; saved news remains intact.
            log.error("RSSHub QQ bootstrap state unavailable: source={}, notificationsSkipped={}",
                    sourceId, saved.size(), exception);
            return;
        }
        if (saved.isEmpty()) {
            return;
        }
        var sender = senders.getIfAvailable();
        if (sender == null) {
            log.info("RSSHub QQ sender disabled: source={}, notificationsSkipped={}", sourceId, saved.size());
            return;
        }
        int limit = properties.getNotificationBatchLimit();
        var selected = saved.subList(0, Math.min(limit, saved.size()));
        var delivery = notifications.notifyNewItems(selected, sender);
        log.info("RSSHub QQ notifications: source={}, saved={}, attempted={}, sent={}, failed={}, limitSkipped={}",
                sourceId, saved.size(), selected.size(), delivery.sent(), delivery.failed(), saved.size() - selected.size());
    }
}
