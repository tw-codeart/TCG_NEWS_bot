package com.twcodeart.tcgnews.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface QqNewsDeliveryRepository extends JpaRepository<QqNewsDelivery, Long> {
    List<QqNewsDelivery> findBySourceIdOrderByNewsIdAsc(String sourceId);
    List<QqNewsDelivery> findBySourceIdAndPendingTrueOrderByNewsIdAsc(String sourceId, Pageable page);
    long countBySourceIdAndPending(String sourceId, boolean pending);
    Optional<QqNewsDelivery> findFirstBySourceIdAndLastAttemptAtIsNotNullOrderByLastAttemptAtDescNewsIdDesc(String sourceId);
    Optional<QqNewsDelivery> findFirstBySourceIdAndSentAtIsNotNullOrderBySentAtDescNewsIdDesc(String sourceId);
}
