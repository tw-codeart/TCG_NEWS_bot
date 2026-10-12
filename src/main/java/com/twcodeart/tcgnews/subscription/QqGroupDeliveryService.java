package com.twcodeart.tcgnews.subscription;

import com.twcodeart.tcgnews.diagnostics.DiagnosticIds;
import com.twcodeart.tcgnews.news.*;
import com.twcodeart.tcgnews.notification.*;
import com.twcodeart.tcgnews.notification.qq.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@ConditionalOnProperty(prefix="qq",name="group-subscriptions-enabled",havingValue="true")
public class QqGroupDeliveryService {
    private static final Logger log=LoggerFactory.getLogger(QqGroupDeliveryService.class);
    private final QqSubscriptionStore groups;
    private final QqDeliveryStore legacy;
    private final QqProperties properties;
    private final QqNotificationSender sender;
    private final NewsNotificationService notifications;
    private int nextTarget;
    public QqGroupDeliveryService(QqSubscriptionStore groups,QqDeliveryStore legacy,QqProperties properties,
                                 QqNotificationSender sender,NewsNotificationService notifications){
        this.groups=groups;this.legacy=legacy;this.properties=properties;this.sender=sender;this.notifications=notifications;
    }
    public void prepare(String source){
        groups.seedLegacy(QqNewsTopics.game(source),properties.groupId());
    }
    public void capture(String source,NewsIngestionService.IngestionResult result,boolean completed){
        try{
            // Per-source eligibility precedes per-game group capture. A newly enabled account's
            // historical rows must never enter an already subscribed game's queue.
            legacy.capture(source,completed&&result.fetched()>0);
            groups.captureEligible(QqNewsTopics.game(source));
        }catch(RuntimeException exception){log.error("Group queue capture failed; other sources continue: errorId={}, source={}",DiagnosticIds.current(),source,exception);}
    }
    public void deliver(List<String> sources,int limit){
        var queues=new LinkedHashMap<QqSubscriptionStore.Target,Deque<List<NewsItem>>>();
        for(String source:sources){
            if(!legacy.status(source).bootstrapComplete())continue;
            for(var target:groups.targets(QqNewsTopics.game(source))){
                if(queues.containsKey(target))continue;
                var packets=NewsForwardPackets.plan(groups.pending(target),sender.acceptsImages()?QqNotificationSender.MAX_FORWARD_ITEMS:1);
                if(!packets.isEmpty())queues.put(target,new ArrayDeque<>(packets));
            }
        }
        var targets=new ArrayList<>(queues.keySet());if(targets.isEmpty())return;
        int cursor=Math.floorMod(nextTarget,targets.size()),attempted=0,idle=0;
        while(attempted<limit&&idle<targets.size()){
            var target=targets.get(cursor);cursor=(cursor+1)%targets.size();
            var packets=queues.get(target);
            if(packets.isEmpty()||!groups.active(target.game(),target.groupId())){idle++;continue;}
            idle=0;attempted++;var packet=packets.removeFirst();
            try{notifications.sendCompleteBatch(packet,sender.forSubscribedGroup(target.groupId()));}
            catch(RuntimeException exception){
                String id=DiagnosticIds.current();String reason=exception instanceof QqNotificationException qq?qq.getReason().name():"FORMATTING_FAILURE";
                groups.attempted(target,packet,false,id,reason);packets.clear();
                log.error("Group delivery failed; kept pending: errorId={}, game={}, group={}",id,target.game(),target.groupId(),exception);continue;
            }
            groups.attempted(target,packet,true,null,null);
            log.info("Group delivery confirmed: game={}, group={}, records={}",target.game(),target.groupId(),packet.size());
        }
        nextTarget=cursor;
    }
}
