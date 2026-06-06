package org.bank.moneytransfer.repository;

import org.bank.moneytransfer.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
