package com.twcodeart.tcgnews.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Durable boundary between old history and newly saved official X records. */
@Entity
@Table(name = "qq_delivery_cursors")
public class QqDeliveryCursor {
    @Id @Column(length = 100) private String sourceId;
    @Column(nullable = false) private long lastNewsId;

    protected QqDeliveryCursor() { }
    public QqDeliveryCursor(String sourceId, long lastNewsId) {
        this.sourceId = sourceId; this.lastNewsId = lastNewsId;
    }
    public long getLastNewsId() { return lastNewsId; }
    public void advance(long value) { lastNewsId = Math.max(lastNewsId, value); }
}
