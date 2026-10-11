package com.twcodeart.tcgnews.subscription;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.notification.qq.*;
import com.twcodeart.tcgnews.source.NewsSource;
import com.twcodeart.tcgnews.storage.GameStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@ConditionalOnProperty(prefix="qq",name="group-subscriptions-enabled",havingValue="true")
public class QqGroupCommandService {
    private static final Logger log=LoggerFactory.getLogger(QqGroupCommandService.class);
    public static final String HELP="[TCG News Bot]\n/news subscribe ws\n/news subscribe ocg\n/news unsubscribe ws\n/news unsubscribe ocg\n/news status\n订阅与取消订阅仅限群主或管理员。新订阅不会补发历史。";
    private final QqSubscriptionStore store;
    private final QqNotificationSender sender;
    private final List<String> sources;
    public QqGroupCommandService(QqSubscriptionStore store,QqNotificationSender sender,QqProperties properties,GameStorage storage,List<NewsSource> sources){
        properties.requireConfigured();
        if(!storage.isSeparated())throw new IllegalArgumentException("Group subscriptions require separated game storage");
        if(properties.getCommandSecret()==null||properties.getCommandSecret().isBlank()||properties.getCommandSecret().length()<16)throw new IllegalArgumentException("qq.command-secret must contain at least 16 characters");
        this.store=store;this.sender=sender;this.sources=sources.stream().map(NewsSource::getSourceId).toList();
    }
    public void handle(long selfId,long groupId,long userId,String eventKey,NewsGroupCommand command){
        if(selfId!=sender.botUserId())throw new QqNotificationException(QqNotificationException.Reason.API_FAILURE,"Event bot identity did not match current login");
        String reply;
        if(command.changesSubscription()){
            String role=sender.memberRole(groupId,userId);
            if(!role.equals("owner")&&!role.equals("admin")){
                if(!store.claimCommand(command.game(),eventKey))return;
                reply="只有群主或群管理员可以订阅、取消订阅。";
            }else if(command.action()==NewsGroupCommand.Action.SUBSCRIBE&&!sources.contains(QqNewsTopics.sourceId(command.game()))){
                if(!store.claimCommand(command.game(),eventKey))return;
                reply="该游戏的数据源尚未启用，请联系维护者开启。";
            }else{
                try{
                    if(!store.change(command.game(),groupId,command.action()==NewsGroupCommand.Action.SUBSCRIBE,eventKey))return;
                    reply=(command.game()==Game.WEISS_SCHWARZ?"WS":"游戏王 OCG")
                        +(command.action()==NewsGroupCommand.Action.SUBSCRIBE?" 已订阅：之后新发现的资讯会推送，历史不补发。":" 已取消订阅，待发送内容已取消。");
                }catch(IllegalArgumentException exception){
                    if(!store.claimCommand(command.game(),eventKey))return;
                    reply="已达到活跃订阅群数量上限（每个游戏最多 10 群），请联系维护者。";
                }
            }
        }else{
            if(!store.claimReadCommand(eventKey))return;
            reply=command.action()==NewsGroupCommand.Action.STATUS
                    ? "本群订阅：\nWS："+state(Game.WEISS_SCHWARZ,groupId)+"\n游戏王 OCG："+state(Game.YUGIOH_OCG,groupId)
                    : HELP;
        }
        // Subscription transaction has already committed; reply failure never undoes the change.
        try{sender.forSubscribedGroup(groupId).send(reply);}
        catch(RuntimeException exception){log.error("QQ command reply failed: errorId={}, group={}",com.twcodeart.tcgnews.diagnostics.DiagnosticIds.current(),groupId,exception);}
    }
    private String state(Game game,long groupId){return (store.active(game,groupId)?"已订阅":"未订阅")
            +(sources.contains(QqNewsTopics.sourceId(game))?"":"（来源未启用）");}
}
