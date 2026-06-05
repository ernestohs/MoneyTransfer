package org.bank.moneytransfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "idempotency_key"}))
public class IdempotencyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String ownerId;

    @Column(nullable = false, length = 180)
    private String idempotencyKey;

    @Column(nullable = false, length = 128)
    private String requestHash;

    @Column(nullable = false, columnDefinition = "text")
    private String responseBody;

    @Column(nullable = false)
    private int statusCode;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String ownerId, String idempotencyKey, String requestHash, String responseBody, int statusCode) {
        this.ownerId = ownerId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.statusCode = statusCode;
        this.expiresAt = OffsetDateTime.now().plusHours(24);
    }

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public String getRequestHash() { return requestHash; }
    public String getResponseBody() { return responseBody; }
    public int getStatusCode() { return statusCode; }
}
