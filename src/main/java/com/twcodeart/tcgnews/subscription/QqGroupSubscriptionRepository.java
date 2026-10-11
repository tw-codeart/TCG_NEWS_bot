package com.twcodeart.tcgnews.subscription;
import com.twcodeart.tcgnews.news.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface QqGroupSubscriptionRepository extends JpaRepository<QqGroupSubscription,QqGroupSubscription.Id> {
    List<QqGroupSubscription> findByIdGameAndActiveTrueOrderByIdGroupIdAsc(Game game);
    long countByIdGameAndActiveTrue(Game game);
}
