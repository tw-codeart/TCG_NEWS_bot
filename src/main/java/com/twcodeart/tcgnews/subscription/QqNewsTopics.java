package com.twcodeart.tcgnews.subscription;
import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.source.ws.WsRssHubNewsSource;
import com.twcodeart.tcgnews.source.ocg.OcgRssHubNewsSource;
public final class QqNewsTopics {
    private QqNewsTopics(){ }
    public static String sourceId(Game game){return game==Game.WEISS_SCHWARZ?WsRssHubNewsSource.SOURCE_ID:OcgRssHubNewsSource.SOURCE_ID;}
    public static String prefix(Game game){return game==Game.WEISS_SCHWARZ?"https://x.com/wstcg/status/":"https://x.com/YuGiOh_OCG_INFO/status/";}
    public static Game game(String source){
        return com.twcodeart.tcgnews.notification.RssHubNotificationSources.topic(source).game();
    }
}
