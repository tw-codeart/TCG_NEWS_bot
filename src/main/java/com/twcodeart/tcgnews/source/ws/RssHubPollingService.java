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

    public RssHubPollingService(NewsFetchService fetch, RssHubProperties properties) {
        this.fetch = fetch;
        this.properties = properties;
        log.info("RSSHub polling enabled: interval={}, ocgEnabled={}", properties.getPollingInterval(), properties.isOcgEnabled());
    }

    @Scheduled(fixedDelayString = "#{@rssHubProperties.pollingInterval.toMillis()}",
            initialDelayString = "#{@rssHubProperties.pollingInterval.toMillis()}")
    public void poll() {
        try {
            var result = properties.isOcgEnabled()
                    ? fetch.fetchSourcesByIds(java.util.List.of(WsRssHubNewsSource.SOURCE_ID,
                            com.twcodeart.tcgnews.source.ocg.OcgRssHubNewsSource.SOURCE_ID))
                    : fetch.fetchSource(WsRssHubNewsSource.SOURCE_ID);
            log.info("RSSHub polling complete: fetched={}, saved={}, duplicates={}, errors={}",
                    result.fetched(), result.saved(), result.duplicates(), result.errors().size());
        } catch (RuntimeException exception) {
            log.error("RSSHub polling failed; later scheduled checks remain enabled", exception);
        }
    }
}
