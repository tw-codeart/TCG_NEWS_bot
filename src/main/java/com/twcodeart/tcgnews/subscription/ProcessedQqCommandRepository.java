package com.twcodeart.tcgnews.subscription;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProcessedQqCommandRepository extends JpaRepository<ProcessedQqCommand,String>{
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from ProcessedQqCommand c where c.processedAt < :cutoff and c.boundGroupId is null")
    int deleteExpired(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);
}
