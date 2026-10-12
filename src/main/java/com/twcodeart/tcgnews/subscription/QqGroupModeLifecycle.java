package com.twcodeart.tcgnews.subscription;
import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.notification.qq.QqProperties;
import com.twcodeart.tcgnews.storage.GameStorage;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
@Component
public class QqGroupModeLifecycle implements ApplicationRunner {
    private final QqProperties properties;private final GameStorage storage;
    private final QqSubscriptionStore subscriptions;private final java.util.List<com.twcodeart.tcgnews.source.NewsSource> sources;
    public QqGroupModeLifecycle(QqProperties properties,GameStorage storage,QqSubscriptionStore subscriptions,
            java.util.List<com.twcodeart.tcgnews.source.NewsSource> sources){this.properties=properties;this.storage=storage;this.subscriptions=subscriptions;this.sources=sources;}
    public void run(ApplicationArguments args){
        if(properties.isGroupSubscriptionsEnabled()){
            for(var source:sources)if(com.twcodeart.tcgnews.notification.RssHubNotificationSources.supports(source.getSourceId()))
                subscriptions.seedLegacy(QqNewsTopics.game(source.getSourceId()),properties.groupId());
            return;
        }
        for(var game:Game.values())if(storage.execute(game,repos -> repos.subscriptions().count())>0)
            throw new IllegalStateException("Group subscriptions were already activated; keep QQ_GROUP_SUBSCRIPTIONS_ENABLED=true to preserve delivery state");
    }
}
