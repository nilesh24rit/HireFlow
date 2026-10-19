package com.hireflow.application.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "application_outbox")
public class ApplicationOutbox {

    public enum DeliveryStatus {
        PENDING, IN_PROGRESS, PUBLISHED
    }

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    protected ApplicationOutbox() {
    }

    public ApplicationOutbox(UUID id, UUID aggregateId, String eventType, int eventVersion,
            String payload, Instant createdAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.payload = payload;
        this.status = DeliveryStatus.PENDING;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public int getEventVersion() { return eventVersion; }
    public String getPayload() { return payload; }
    public DeliveryStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }

    public void markInProgress(Instant claimUntil) {
        status = DeliveryStatus.IN_PROGRESS;
        attemptCount++;
        nextAttemptAt = claimUntil;
    }

    public void markPublished(Instant publishedAt) {
        status = DeliveryStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        lastError = null;
    }

    public void markRetry(Instant nextAttemptAt, String error) {
        status = DeliveryStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        lastError = error;
    }

    public void recover(Instant nextAttemptAt, String error) {
        status = DeliveryStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        lastError = error;
    }
}
