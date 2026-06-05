package org.bank.moneytransfer.repository;

import java.time.OffsetDateTime;
import java.util.List;
import org.bank.moneytransfer.domain.OutboxEvent;
import org.bank.moneytransfer.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop25ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(OutboxStatus status, OffsetDateTime now);
}
