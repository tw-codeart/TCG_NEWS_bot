package com.twcodeart.tcgnews.source.ocg;

import com.rometools.rome.io.FeedException;
import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.news.NewsItem;
import com.twcodeart.tcgnews.source.rsshub.OfficialXCardFeedParser;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;

@Component
public class OcgRssHubFeedParser {
    private final OfficialXCardFeedParser parser = new OfficialXCardFeedParser(
            Game.YUGIOH_OCG, "YuGiOh_OCG_INFO", new OcgNewCardPostFilter(), false, true);
    public List<NewsItem> parse(byte[] rawFeed) throws FeedException, IOException { return parser.parse(rawFeed); }
}
