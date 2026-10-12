package com.twcodeart.tcgnews.api;

import com.twcodeart.tcgnews.notification.QqDeliveryStore;
import com.twcodeart.tcgnews.notification.qq.QqNotificationException;
import com.twcodeart.tcgnews.notification.qq.QqProperties;
import com.twcodeart.tcgnews.source.NewsSource;
import com.twcodeart.tcgnews.source.ws.RssHubPollingStatus;
import com.twcodeart.tcgnews.source.ws.WsRssHubNewsSource;
import com.twcodeart.tcgnews.source.ocg.OcgRssHubNewsSource;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class QqDeliveryStatusController {
    private final QqDeliveryStore store;
    private final QqProperties qq;
    private final RssHubPollingStatus polling;
    private final Environment environment;
    private final List<NewsSource> sources;
    private final com.twcodeart.tcgnews.subscription.QqSubscriptionStore subscriptions;

    public QqDeliveryStatusController(QqDeliveryStore store, QqProperties qq, RssHubPollingStatus polling,
                                       Environment environment, List<NewsSource> sources,com.twcodeart.tcgnews.subscription.QqSubscriptionStore subscriptions) {
        this.store = store; this.qq = qq; this.polling = polling; this.environment = environment; this.sources = List.copyOf(sources);
        this.subscriptions=subscriptions;
    }

    @GetMapping("/api/notifications/status")
    public Status status() {
        String problem = null;
        try { qq.requireConfigured(); }
        catch (QqNotificationException exception) { problem = exception.getMessage(); }
        var ids=sources.stream().map(NewsSource::getSourceId)
                .filter(com.twcodeart.tcgnews.notification.RssHubNotificationSources::supports).toList();
        var rows=qq.isGroupSubscriptionsEnabled()?ids.stream().map(com.twcodeart.tcgnews.subscription.QqNewsTopics::game)
                .distinct().map(subscriptions::status).toList():ids.stream().map(store::status).toList();
        var baselines=ids.stream().map(store::status).map(value->new Baseline(value.sourceId(),value.bootstrapComplete(),value.cursorInitialized())).toList();
        boolean enabled = environment.getProperty("rsshub.enabled", Boolean.class, false)
                && environment.getProperty("rsshub.polling-enabled", Boolean.class, false);
        var groupCounts=qq.isGroupSubscriptionsEnabled()?rows.stream().map(row -> new GroupCount(row.sourceId(),
                subscriptions.groupCount(com.twcodeart.tcgnews.subscription.QqNewsTopics.game(row.sourceId())))).toList():List.<GroupCount>of();
        return new Status("durable-qq-v1", enabled, qq.isEnabled(), problem == null, problem, polling.snapshot(), rows,
                qq.isGroupSubscriptionsEnabled(),groupCounts,baselines);
    }
    public record Status(String deliveryRevision, boolean pollingEnabled, boolean qqEnabled, boolean qqConfigured,
                          String configurationProblem, RssHubPollingStatus.Snapshot polling,
                          List<QqDeliveryStore.SourceStatus> sources,boolean groupSubscriptionsEnabled,List<GroupCount> groupCounts,List<Baseline> baselines) { }
    public record Baseline(String sourceId,boolean bootstrapComplete,boolean cursorInitialized){ }
    public record GroupCount(String sourceId,long activeGroups){ }
}
