package com.twcodeart.tcgnews.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** Transport state is separate from saved news and its content identity. */
@Entity
@Table(name = "qq_news_deliveries", indexes = @Index(name = "idx_qq_pending_source", columnList = "source_id,pending,news_id"))
public class QqNewsDelivery {
    @Id @Column(name = "news_id") private Long newsId;
    @Column(name = "source_id", length = 100, nullable = false) private String sourceId;
    @Column(nullable = false) private boolean pending = true;
    @Column(nullable = false) private int attempts;
    private Instant lastAttemptAt;
    private Instant sentAt;
    @Column(length = 36) private String lastErrorId;
    @Column(length = 40) private String lastErrorReason;

    protected QqNewsDelivery() { }
    public QqNewsDelivery(Long newsId, String sourceId) { this.newsId = newsId; this.sourceId = sourceId; }
    public void attempted(boolean success, String errorId, String reason) {
        attempts++; lastAttemptAt = Instant.now(); lastErrorId = errorId; lastErrorReason = reason;
        if (success) { pending = false; sentAt = lastAttemptAt; }
    }
    public Long getNewsId() { return newsId; }
    public boolean isPending() { return pending; }
    public int getAttempts() { return attempts; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public Instant getSentAt() { return sentAt; }
    public String getLastErrorId() { return lastErrorId; }
    public String getLastErrorReason() { return lastErrorReason; }
}
