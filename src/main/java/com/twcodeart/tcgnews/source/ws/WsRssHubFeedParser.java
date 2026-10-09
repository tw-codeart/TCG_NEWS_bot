package com.twcodeart.tcgnews.source.ws;

import com.rometools.rome.io.FeedException;
import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsItem;
import com.twcodeart.tcgnews.source.rsshub.OfficialXCardFeedParser;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;

@Component
public class WsRssHubFeedParser {
    private final OfficialXCardFeedParser parser = new OfficialXCardFeedParser(
            Game.WEISS_SCHWARZ, "wstcg", text -> text.contains("今日のカード"), true, false);

    public List<NewsItem> parse(byte[] rawFeed) throws FeedException, IOException {
        return parser.parse(rawFeed);
    }
}
