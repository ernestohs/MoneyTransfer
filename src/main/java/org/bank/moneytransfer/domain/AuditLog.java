package org.bank.moneytransfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String publicId;

    @Column(nullable = false, length = 120)
    private String ownerId;

    @Column(nullable = false, length = 120)
    private String actorId;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 80)
    private String resourceType;

    @Column(nullable = false, length = 80)
    private String resourceId;

    @Column(nullable = false, length = 120)
    private String correlationId;

    @Column(columnDefinition = "text")
    private String metadata;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected AuditLog() {
    }

    public AuditLog(String publicId, String ownerId, String actorId, String action,
                    String resourceType, String resourceId, String correlationId, String metadata) {
        this.publicId = publicId;
        this.ownerId = ownerId;
        this.actorId = actorId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.correlationId = correlationId;
        this.metadata = metadata;
    }

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
