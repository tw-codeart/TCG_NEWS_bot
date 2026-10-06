package com.twcodeart.tcgnews.source.ws;

import com.rometools.rome.io.FeedException;
import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsItem;
import com.twcodeart.tcgnews.source.NewsSource;
import com.twcodeart.tcgnews.source.NewsSourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "rsshub", name = "enabled", havingValue = "true")
public class WsRssHubNewsSource implements NewsSource {

    public static final String SOURCE_ID = "ws-jp-rsshub-card-reveals";

    private static final Logger log = LoggerFactory.getLogger(WsRssHubNewsSource.class);
    private final WsRssHubFeedClient client;
    private final WsRssHubFeedParser parser;

    public WsRssHubNewsSource(WsRssHubFeedClient client, WsRssHubFeedParser parser) {
        this.client = client;
        this.parser = parser;
    }

    @Override
    public String getSourceId() {
        return SOURCE_ID;
    }

    @Override
    public Game getGame() {
        return Game.WEISS_SCHWARZ;
    }

    @Override
    public List<NewsItem> fetchNews() throws NewsSourceException {
        try {
            return parser.parse(client.fetch());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(exception);
        } catch (IOException | FeedException | IllegalArgumentException | IllegalStateException exception) {
            throw failure(exception);
        }
    }

    private NewsSourceException failure(Exception exception) {
        log.error("Failed to fetch/parse WS RSSHub feed: source={}", getSourceId(), exception);
        return new NewsSourceException("Failed to fetch/parse WS RSSHub feed for " + getSourceId(), exception);
    }
}
