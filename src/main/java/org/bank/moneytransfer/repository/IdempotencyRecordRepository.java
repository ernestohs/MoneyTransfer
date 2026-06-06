package org.bank.moneytransfer.repository;

import java.util.Optional;
import org.bank.moneytransfer.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByOwnerIdAndIdempotencyKey(String ownerId, String idempotencyKey);
}
