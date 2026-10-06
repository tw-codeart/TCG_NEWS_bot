package com.twcodeart.tcgnews.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/** One baseline marker per source, not a delivery status for individual messages. */
@Entity
@Table(name = "source_notification_bootstraps")
public class SourceNotificationBootstrap {

    @Id
    @Column(length = 100, nullable = false)
    private String sourceId;

    @Column(nullable = false, updatable = false)
    private Instant initializedAt;

    protected SourceNotificationBootstrap() { }

    public SourceNotificationBootstrap(String sourceId) {
        this.sourceId = Objects.requireNonNull(sourceId);
        this.initializedAt = Instant.now();
    }

    public String getSourceId() { return sourceId; }
    public Instant getInitializedAt() { return initializedAt; }
}
