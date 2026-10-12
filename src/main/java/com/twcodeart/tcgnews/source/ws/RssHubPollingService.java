package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.NewsFetchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "rsshub", name = {"enabled", "polling-enabled"}, havingValue = "true")
public class RssHubPollingService {

    private static final Logger log = LoggerFactory.getLogger(RssHubPollingService.class);
    private final NewsFetchService fetch;
    private final RssHubProperties properties;
    private final RssHubPollingStatus status;

    public RssHubPollingService(NewsFetchService fetch, RssHubProperties properties, RssHubPollingStatus status) {
        this.fetch = fetch;
        this.properties = properties;
        this.status = status;
        log.info("RSSHub polling enabled: interval={}, ocgEnabled={}", properties.getPollingInterval(), properties.isOcgEnabled());
    }

    @Scheduled(fixedDelayString = "#{@rssHubProperties.pollingInterval.toMillis()}",
            initialDelayString = "10000")
    public void poll() {
        String id = java.util.UUID.randomUUID().toString();
        String previous = org.slf4j.MDC.get(com.twcodeart.tcgnews.diagnostics.DiagnosticIds.MDC_KEY);
        org.slf4j.MDC.put(com.twcodeart.tcgnews.diagnostics.DiagnosticIds.MDC_KEY, id);
        status.started();
        try {
            var ocgSources=new java.util.ArrayList<String>();ocgSources.add(WsRssHubNewsSource.SOURCE_ID);
            ocgSources.add(com.twcodeart.tcgnews.source.ocg.OcgRssHubNewsSource.SOURCE_ID);
            if(properties.isOcgJpEnabled())ocgSources.add(com.twcodeart.tcgnews.source.ocg.YuGiOhJpRssHubNewsSource.SOURCE_ID);
            var result = properties.isOcgEnabled()
                    ? fetch.fetchSourcesByIds(ocgSources)
                    : fetch.fetchSource(WsRssHubNewsSource.SOURCE_ID);
            status.completed(result, result.errors().isEmpty() ? null : id);
            log.info("RSSHub polling complete: fetched={}, saved={}, duplicates={}, errors={}",
                    result.fetched(), result.saved(), result.duplicates(), result.errors().size());
        } catch (RuntimeException exception) {
            status.failed(id);
            log.error("RSSHub polling failed; later scheduled checks remain enabled", exception);
        } finally {
            if (previous == null) org.slf4j.MDC.remove(com.twcodeart.tcgnews.diagnostics.DiagnosticIds.MDC_KEY);
            else org.slf4j.MDC.put(com.twcodeart.tcgnews.diagnostics.DiagnosticIds.MDC_KEY, previous);
        }
    }
}
