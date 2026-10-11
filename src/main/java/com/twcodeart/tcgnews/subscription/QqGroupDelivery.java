package com.twcodeart.tcgnews.subscription;

import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.notification.QqNewsDelivery;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name="qq_group_deliveries",indexes=@Index(name="idx_group_pending",columnList="game,group_id,pending,news_id"))
public class QqGroupDelivery {
    @EmbeddedId private Id id;
    @Column(nullable=false) private boolean pending=true;
    @Column(nullable=false) private int attempts;
    private Instant lastAttemptAt;
    private Instant sentAt;
    @Column(length=36) private String lastErrorId;
    @Column(length=40) private String lastErrorReason;
    protected QqGroupDelivery(){ }
    public QqGroupDelivery(Game game,long groupId,long newsId){id=new Id(game,groupId,newsId);}
    public static QqGroupDelivery legacy(Game game,long groupId,QqNewsDelivery old){
        var value=new QqGroupDelivery(game,groupId,old.getNewsId());
        value.pending=old.isPending();value.attempts=old.getAttempts();value.lastAttemptAt=old.getLastAttemptAt();
        value.sentAt=old.getSentAt();value.lastErrorId=old.getLastErrorId();value.lastErrorReason=old.getLastErrorReason();
        return value;
    }
    public long getNewsId(){return id.newsId;}
    public boolean isPending(){return pending;}
    public Instant getLastAttemptAt(){return lastAttemptAt;}
    public Instant getSentAt(){return sentAt;}
    public String getLastErrorId(){return lastErrorId;}
    public String getLastErrorReason(){return lastErrorReason;}
    public void attempted(boolean success,String errorId,String reason){
        attempts++;lastAttemptAt=Instant.now();lastErrorId=errorId;lastErrorReason=reason;
        if(success){pending=false;sentAt=lastAttemptAt;}
    }
    @Embeddable public static class Id implements Serializable {
        @Enumerated(EnumType.STRING) @Column(name="game",length=32) private Game game;
        @Column(name="group_id") private Long groupId;
        @Column(name="news_id") private Long newsId;
        protected Id(){ }
        public Id(Game game,long groupId,long newsId){this.game=Objects.requireNonNull(game);this.groupId=groupId;this.newsId=newsId;}
        @Override public boolean equals(Object o){return o instanceof Id other&&game==other.game&&Objects.equals(groupId,other.groupId)&&Objects.equals(newsId,other.newsId);}
        @Override public int hashCode(){return Objects.hash(game,groupId,newsId);}
    }
}
