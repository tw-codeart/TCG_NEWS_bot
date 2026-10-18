package com.twcodeart.tcgnews.notification;

import com.twcodeart.tcgnews.diagnostics.DiagnosticIds;
import com.twcodeart.tcgnews.news.NewsIngestionService;
import com.twcodeart.tcgnews.notification.qq.QqNotificationException;
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
    private final QqDeliveryStore store;
    private final RssHubProperties properties;
    private final ObjectProvider<QqNotificationSender> senders;
    private final NewsNotificationService notifications;

    public RssHubQqNotificationService(QqDeliveryStore store, RssHubProperties properties,
                                       ObjectProvider<QqNotificationSender> senders, NewsNotificationService notifications) {
        this.store = store; this.properties = properties; this.senders = senders; this.notifications = notifications;
    }
    public boolean handles(String sourceId) {
        return RssHubNotificationSources.supports(sourceId);
    }
    public int notificationLimit() { return properties.getNotificationBatchLimit(); }
    public void prepareSource(String sourceId) { store.prepare(sourceId); }

    /** Establish history/queue state without invoking any notification transport. */
    public void captureSource(String sourceId, boolean completedNonemptySnapshot) {
        store.capture(sourceId, completedNonemptySnapshot);
    }

    public int notifySavedItems(String sourceId, NewsIngestionService.IngestionResult result,
                                boolean ingestionCompleted, int availableMessages) {
        try {
            store.capture(sourceId, ingestionCompleted && result.fetched() > 0);
            var status = store.status(sourceId);
            if (!status.bootstrapComplete()) {
                log.info("RSSHub history baseline waiting: source={}, saved={}", sourceId, result.savedItems().size());
                return 0;
            }
            var pending = store.pending(sourceId);
            if (pending.isEmpty()) return 0;
            var sender = senders.getIfAvailable();
            if (sender == null) {
                log.warn("QQ sender disabled; news remains pending: source={}, pendingItems={}", sourceId, status.pendingItems());
                return 0;
            }
            int limit = Math.max(0, Math.min(notificationLimit(), availableMessages));
            var packets = NewsForwardPackets.plan(pending, sender.acceptsImages() ? QqNotificationSender.MAX_FORWARD_ITEMS : 1);
            int attempted = 0;
            for (var packet : packets) {
                if (attempted >= limit) break;
                attempted++;
                try {
                    notifications.sendCompleteBatch(packet, sender);
                } catch (RuntimeException exception) {
                    String id = DiagnosticIds.current();
                    String reason = exception instanceof QqNotificationException qq ? qq.getReason().name() : "FORMATTING_FAILURE";
                    store.recordAttempt(packet, false, id, reason);
                    log.error("QQ delivery failed; kept pending: errorId={}, source={}, records={}, reason={}",
                            id, sourceId, packet.size(), reason, exception);
                    // Stop this source after one failure, rather than immediately retrying or flooding it.
                    break;
                }
                store.recordAttempt(packet, true, null, null);
                log.info("QQ delivery confirmed: source={}, records={}", sourceId, packet.size());
            }
            log.info("RSSHub QQ delivery complete: source={}, packetsAttempted={}, pendingItems={}",
                    sourceId, attempted, store.status(sourceId).pendingItems());
            return attempted;
        } catch (RuntimeException exception) {
            log.error("QQ delivery state unavailable; news remains saved: errorId={}, source={}",
                    DiagnosticIds.current(), sourceId, exception);
            // Exhaust the budget if transport succeeded but its acknowledgement could not be saved.
            return Math.max(0, availableMessages);
        }
    }
}
