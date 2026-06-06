package org.bank.moneytransfer.domain;

public enum TransferStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REVERSED
}
