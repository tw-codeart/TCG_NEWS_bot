package com.twcodeart.tcgnews.source.ws;
import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.source.rsshub.PostImageContentHash;
final class WsRssHubContentHash {
    private WsRssHubContentHash() { }
    static String generate(String sourceUrl, String imageUrl) {
        return PostImageContentHash.generate(Game.WEISS_SCHWARZ, sourceUrl, imageUrl);
    }
}
