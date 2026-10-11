package com.twcodeart.tcgnews.subscription;
import com.twcodeart.tcgnews.news.Game;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface QqGroupDeliveryRepository extends JpaRepository<QqGroupDelivery,QqGroupDelivery.Id> {
    List<QqGroupDelivery> findByIdGameAndIdGroupIdAndPendingTrueOrderByIdNewsIdAsc(Game game,long groupId,Pageable page);
    long countByIdGameAndIdGroupIdAndPending(Game game,long groupId,boolean pending);
    long countByIdGameAndPending(Game game,boolean pending);
    void deleteByIdGameAndIdGroupIdAndPendingTrue(Game game,long groupId);
    Optional<QqGroupDelivery> findFirstByIdGameAndLastAttemptAtIsNotNullOrderByLastAttemptAtDesc(Game game);
    Optional<QqGroupDelivery> findFirstByIdGameAndSentAtIsNotNullOrderBySentAtDesc(Game game);
}
