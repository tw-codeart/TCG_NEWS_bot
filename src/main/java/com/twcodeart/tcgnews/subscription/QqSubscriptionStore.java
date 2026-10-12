package com.twcodeart.tcgnews.subscription;

import com.twcodeart.tcgnews.news.*;
import com.twcodeart.tcgnews.notification.QqDeliveryStore;
import com.twcodeart.tcgnews.storage.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class QqSubscriptionStore {
    private final GameStorage storage;
    public QqSubscriptionStore(GameStorage storage){this.storage=storage;}

    public void seedLegacy(Game game,long groupId){
        storage.execute(game,repos -> {
            String key=hash("legacy-group-binding|"+game);
            var marker=repos.commands().findById(key).orElse(null);
            if(marker!=null){
                if(!Objects.equals(marker.getBoundGroupId(),groupId))throw new IllegalStateException("Configured test group changed; retain it and add groups with subscription commands");
                return null;
            }
            var id=new QqGroupSubscription.Id(game,groupId);
            if(!repos.subscriptions().existsById(id)){
                repos.subscriptions().save(new QqGroupSubscription(game,groupId,latest(game,repos),true));
                for(var topic:com.twcodeart.tcgnews.notification.RssHubNotificationSources.forGame(game))
                    for(var old:repos.deliveries().findBySourceIdOrderByNewsIdAsc(topic.id()))
                        repos.groupDeliveries().save(QqGroupDelivery.legacy(game,groupId,old));
            }
            repos.commands().save(new ProcessedQqCommand(key,groupId));return null;
        });
    }

    /** Preference change and retry-dedup marker commit together in this game's DB. */
    public boolean change(Game game,long groupId,boolean subscribe,String eventKey){
        return storage.execute(game,repos -> {
            if(repos.commands().existsById(eventKey))return false;
            repos.commands().saveAndFlush(new ProcessedQqCommand(eventKey));
            var id=new QqGroupSubscription.Id(game,groupId);
            var value=repos.subscriptions().findById(id).orElse(null);
            if(subscribe&&(value==null||!value.isActive())){
                if(repos.subscriptions().countByIdGameAndActiveTrue(game)>=10)throw new IllegalArgumentException("Active group limit reached");
                repos.groupDeliveries().deleteByIdGameAndIdGroupIdAndPendingTrue(game,groupId);
                if(value==null)repos.subscriptions().save(new QqGroupSubscription(game,groupId,latest(game,repos),true));
                else value.activate(latest(game,repos));
            }else if(!subscribe){
                if(value==null)repos.subscriptions().save(new QqGroupSubscription(game,groupId,latest(game,repos),false));
                else value.cancel();
                repos.groupDeliveries().deleteByIdGameAndIdGroupIdAndPendingTrue(game,groupId);
            }
            return true;
        });
    }

    public boolean claimReadCommand(String key){return claimCommand(Game.WEISS_SCHWARZ,key);}
    public boolean claimCommand(Game game,String key){return storage.execute(game,repos -> {
        if(repos.commands().existsById(key))return false;
        repos.commands().saveAndFlush(new ProcessedQqCommand(key));return true;
    });}

    public void captureEligible(Game game){storage.execute(game,repos -> {
        for(var subscription:repos.subscriptions().findByIdGameAndActiveTrueOrderByIdGroupIdAsc(game)){
            var items=repos.news().findEligibleQqGroupCandidates(game,NewsCategory.CARD_REVEAL,
                    subscription.getLastNewsId(),PageRequest.of(0,500));
            for(var item:items){
                var id=new QqGroupDelivery.Id(game,subscription.getGroupId(),item.getId());
                if(!repos.groupDeliveries().existsById(id))repos.groupDeliveries().save(new QqGroupDelivery(game,subscription.getGroupId(),item.getId()));
                subscription.advance(item.getId());
            }
        }
        return null;
    });}

    public List<Target> targets(Game game){return storage.execute(game,repos -> repos.subscriptions()
            .findByIdGameAndActiveTrueOrderByIdGroupIdAsc(game).stream()
            .filter(sub -> repos.groupDeliveries().countByIdGameAndIdGroupIdAndPending(game,sub.getGroupId(),true)>0)
            .map(sub -> new Target(game,sub.getGroupId())).toList());}
    public List<NewsItem> pending(Target target){return storage.execute(target.game(),repos -> {
        var sub=repos.subscriptions().findById(new QqGroupSubscription.Id(target.game(),target.groupId())).orElse(null);
        if(sub==null||!sub.isActive())return List.of();
        var ids=repos.groupDeliveries().findByIdGameAndIdGroupIdAndPendingTrueOrderByIdNewsIdAsc(target.game(),target.groupId(),PageRequest.of(0,500))
                .stream().map(QqGroupDelivery::getNewsId).toList();
        return repos.news().findAllById(ids).stream().sorted(Comparator.comparing(NewsItem::getId)).toList();
    });}
    public void attempted(Target target,List<NewsItem> items,boolean success,String errorId,String reason){storage.execute(target.game(),repos -> {
        for(var item:items){
            if(item.getGame()!=target.game())throw new IllegalArgumentException("Delivery mixes games");
            repos.groupDeliveries().findById(new QqGroupDelivery.Id(target.game(),target.groupId(),item.getId()))
                    .ifPresent(value -> value.attempted(success,errorId,reason));
        }
        return null;
    });}
    public boolean active(Game game,long groupId){return storage.execute(game,repos -> repos.subscriptions()
            .findById(new QqGroupSubscription.Id(game,groupId)).map(QqGroupSubscription::isActive).orElse(false));}
    public long groupCount(Game game){return storage.execute(game,repos -> repos.subscriptions().countByIdGameAndActiveTrue(game));}
    public QqDeliveryStore.SourceStatus status(Game game){return storage.execute(game,repos -> {
        var last=repos.groupDeliveries().findFirstByIdGameAndLastAttemptAtIsNotNullOrderByLastAttemptAtDesc(game).orElse(null);
        var sent=repos.groupDeliveries().findFirstByIdGameAndSentAtIsNotNullOrderBySentAtDesc(game).orElse(null);
        return new QqDeliveryStore.SourceStatus(QqNewsTopics.sourceId(game),repos.bootstraps().existsById(QqNewsTopics.sourceId(game)),
                repos.cursors().existsById(QqNewsTopics.sourceId(game)),repos.groupDeliveries().countByIdGameAndPending(game,true),
                repos.groupDeliveries().countByIdGameAndPending(game,false),last==null?null:last.getLastAttemptAt(),
                sent==null?null:sent.getSentAt(),last==null?null:last.getLastErrorId(),last==null?null:last.getLastErrorReason());
    });}
    public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private long latest(Game game,GameRepositories repos){return com.twcodeart.tcgnews.notification.RssHubNotificationSources.forGame(game).stream()
            .mapToLong(topic->repos.news().findLatestQqDeliveryId(game,NewsCategory.CARD_REVEAL,topic.prefix())).max().orElse(0);}
    public record Target(Game game,long groupId){ }
}
