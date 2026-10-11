package com.twcodeart.tcgnews.subscription;

import com.twcodeart.tcgnews.news.Game;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name="qq_group_subscriptions")
public class QqGroupSubscription {
    @EmbeddedId private Id id;
    @Version private long version;
    @Column(nullable=false) private boolean active;
    @Column(nullable=false) private long lastNewsId;
    @Column(nullable=false) private Instant changedAt;
    protected QqGroupSubscription() { }
    public QqGroupSubscription(Game game,long groupId,long cutoff,boolean active) {
        id=new Id(game,groupId);lastNewsId=cutoff;this.active=active;changedAt=Instant.now();
    }
    public boolean isActive(){return active;}
    public long getGroupId(){return id.groupId;}
    public long getLastNewsId(){return lastNewsId;}
    public void advance(long value){lastNewsId=Math.max(lastNewsId,value);}
    public void activate(long cutoff){active=true;lastNewsId=cutoff;changedAt=Instant.now();}
    public void cancel(){active=false;changedAt=Instant.now();}

    @Embeddable public static class Id implements Serializable {
        @Enumerated(EnumType.STRING) @Column(name="game",length=32) private Game game;
        @Column(name="group_id") private Long groupId;
        protected Id(){ }
        public Id(Game game,long groupId){this.game=Objects.requireNonNull(game);if(groupId<1)throw new IllegalArgumentException("Invalid group");this.groupId=groupId;}
        @Override public boolean equals(Object o){return o instanceof Id other&&game==other.game&&Objects.equals(groupId,other.groupId);}
        @Override public int hashCode(){return Objects.hash(game,groupId);}
    }
}
