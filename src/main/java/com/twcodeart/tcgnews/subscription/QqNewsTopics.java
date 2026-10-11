package com.twcodeart.tcgnews.subscription;
import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.source.ws.WsRssHubNewsSource;
import com.twcodeart.tcgnews.source.ocg.OcgRssHubNewsSource;
public final class QqNewsTopics {
    private QqNewsTopics(){ }
    public static String sourceId(Game game){return game==Game.WEISS_SCHWARZ?WsRssHubNewsSource.SOURCE_ID:OcgRssHubNewsSource.SOURCE_ID;}
    public static String prefix(Game game){return game==Game.WEISS_SCHWARZ?"https://x.com/wstcg/status/":"https://x.com/YuGiOh_OCG_INFO/status/";}
    public static Game game(String source){
        if(WsRssHubNewsSource.SOURCE_ID.equals(source))return Game.WEISS_SCHWARZ;
        if(OcgRssHubNewsSource.SOURCE_ID.equals(source))return Game.YUGIOH_OCG;
        throw new IllegalArgumentException("Unsupported QQ news source");
    }
}
