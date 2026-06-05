package org.bank.moneytransfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String publicId;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false, length = 80)
    private String aggregateType;

    @Column(nullable = false, length = 80)
    private String aggregateId;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(length = 128)
    private String signature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String publicId, String eventType, String aggregateType, String aggregateId,
                       String payload, String signature) {
        this.publicId = publicId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.signature = signature;
        this.nextAttemptAt = OffsetDateTime.now();
    }

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public void markPublished() {
        status = OutboxStatus.PUBLISHED;
        publishedAt = OffsetDateTime.now();
    }

    public void markRetry() {
        retryCount++;
        long delaySeconds = Math.min(3600, 1L << Math.min(retryCount, 10));
        nextAttemptAt = OffsetDateTime.now().plusSeconds(delaySeconds);
        status = retryCount >= 10 ? OutboxStatus.FAILED : OutboxStatus.PENDING;
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public OutboxStatus getStatus() { return status; }
}
