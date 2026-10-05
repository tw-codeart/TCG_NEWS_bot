package com.twcodeart.tcgnews.source.ws;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsItem;
import com.twcodeart.tcgnews.source.NewsSource;
import com.twcodeart.tcgnews.source.NewsSourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class WsInformationSource implements NewsSource {

    private static final Logger log = LoggerFactory.getLogger(WsInformationSource.class);
    private final WsInformationPageClient client;
    private final WsInformationParser parser;

    public WsInformationSource(WsInformationPageClient client, WsInformationParser parser) {
        this.client = client;
        this.parser = parser;
    }

    @Override
    public String getSourceId() {
        return "ws-jp-information";
    }

    @Override
    public Game getGame() {
        return Game.WEISS_SCHWARZ;
    }

    @Override
    public List<NewsItem> fetchNews() throws NewsSourceException {
        try {
            return parser.parse(client.fetch());
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            log.error("Failed to fetch WS information from {}", WsInformationPageClient.INFORMATION_URL, exception);
            throw new NewsSourceException("Failed to fetch WS information from "
                    + WsInformationPageClient.INFORMATION_URL, exception);
        }
    }
}
