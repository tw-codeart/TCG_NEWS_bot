package com.twcodeart.tcgnews.source.ocg;

import com.rometools.rome.io.FeedException;
import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsItem;
import com.twcodeart.tcgnews.source.NewsSource;
import com.twcodeart.tcgnews.source.NewsSourceException;
import com.twcodeart.tcgnews.source.rsshub.RssHubFeedClient;
import com.twcodeart.tcgnews.source.ws.RssHubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "rsshub", name = {"enabled", "ocg-enabled"}, havingValue = "true")
public class OcgRssHubNewsSource implements NewsSource {
    public static final String SOURCE_ID = "ocg-jp-rsshub-card-reveals";
    private static final Logger log = LoggerFactory.getLogger(OcgRssHubNewsSource.class);
    private final RssHubFeedClient client;
    private final OcgRssHubFeedParser parser;

    @Autowired
    public OcgRssHubNewsSource(RssHubProperties properties, OcgRssHubFeedParser parser) {
        this(new RssHubFeedClient(properties.ocgFeedUri()), parser);
    }
    public OcgRssHubNewsSource(RssHubFeedClient client, OcgRssHubFeedParser parser) {
        this.client = client;
        this.parser = parser;
    }
    @Override public String getSourceId() { return SOURCE_ID; }
    @Override public Game getGame() { return Game.YUGIOH_OCG; }
    @Override public List<NewsItem> fetchNews() throws NewsSourceException {
        try { return parser.parse(client.fetch()); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw failure(exception); }
        catch (IOException | FeedException | IllegalArgumentException | IllegalStateException exception) { throw failure(exception); }
    }
    private NewsSourceException failure(Exception exception) {
        log.error("Failed to fetch/parse OCG RSSHub feed: source={}", SOURCE_ID, exception);
        return new NewsSourceException("Failed to fetch/parse OCG RSSHub feed for " + SOURCE_ID, exception);
    }
}
