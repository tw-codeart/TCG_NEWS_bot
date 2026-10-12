package com.twcodeart.tcgnews.notification;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.source.ocg.OcgRssHubNewsSource;
import com.twcodeart.tcgnews.source.ocg.YuGiOhJpRssHubNewsSource;
import com.twcodeart.tcgnews.source.ws.WsRssHubNewsSource;
import java.util.List;

/** Source identity and official post scope, shared by delivery and stored-post queries. */
public final class RssHubNotificationSources {
    private static final List<Topic> TOPICS=List.of(
            new Topic(WsRssHubNewsSource.SOURCE_ID,Game.WEISS_SCHWARZ,"https://x.com/wstcg/status/"),
            new Topic(OcgRssHubNewsSource.SOURCE_ID,Game.YUGIOH_OCG,"https://x.com/YuGiOh_OCG_INFO/status/"),
            new Topic(YuGiOhJpRssHubNewsSource.SOURCE_ID,Game.YUGIOH_OCG,"https://x.com/yu_gi_oh_jp/status/"));
    public static boolean supports(String id){return TOPICS.stream().anyMatch(topic->topic.id().equals(id));}
    public static Topic topic(String id){return TOPICS.stream().filter(topic->topic.id().equals(id)).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Unsupported RSSHub notification source"));}
    public static List<Topic> forGame(Game game){return TOPICS.stream().filter(topic->topic.game()==game).toList();}
    public record Topic(String id,Game game,String prefix){ }
    private RssHubNotificationSources(){ }
}
