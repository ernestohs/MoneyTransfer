package org.bank.moneytransfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "transfers")
public class Transfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String publicId;

    @Column(nullable = false, length = 120)
    private String ownerId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "destination_account_id")
    private Account destinationAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status;

    @Column(length = 180)
    private String idempotencyKey;

    @Column(length = 500)
    private String description;

    @Column(length = 500)
    private String failureReason;

    @ManyToOne
    @JoinColumn(name = "reversal_of_transfer_id")
    private Transfer reversalOfTransfer;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected Transfer() {
    }

    public Transfer(String publicId, String ownerId, Account sourceAccount, Account destinationAccount,
                    BigDecimal amount, String currency, String idempotencyKey, String description) {
        this.publicId = publicId;
        this.ownerId = ownerId;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.description = description;
        this.status = TransferStatus.PROCESSING;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void complete() { status = TransferStatus.COMPLETED; }
    public void cancel() { status = TransferStatus.CANCELLED; }
    public void reverse() { status = TransferStatus.REVERSED; }
    public void fail(String reason) {
        status = TransferStatus.FAILED;
        failureReason = reason;
    }
    public void markReversalOf(Transfer original) { reversalOfTransfer = original; }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getOwnerId() { return ownerId; }
    public Account getSourceAccount() { return sourceAccount; }
    public Account getDestinationAccount() { return destinationAccount; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public TransferStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public String getFailureReason() { return failureReason; }
    public Transfer getReversalOfTransfer() { return reversalOfTransfer; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
