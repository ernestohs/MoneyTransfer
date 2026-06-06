package org.bank.moneytransfer.service;

import org.bank.moneytransfer.domain.AuditLog;
import org.bank.moneytransfer.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository repository;
    private final IdGenerator ids;

    public AuditService(AuditLogRepository repository, IdGenerator ids) {
        this.repository = repository;
        this.ids = ids;
    }

    public void record(String ownerId, String actorId, String action, String resourceType,
                       String resourceId, String correlationId, String metadata) {
        repository.save(new AuditLog(ids.auditId(), ownerId, actorId, action, resourceType, resourceId, correlationId, metadata));
    }
}
