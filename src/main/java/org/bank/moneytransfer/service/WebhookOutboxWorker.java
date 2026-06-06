package org.bank.moneytransfer.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import org.bank.moneytransfer.domain.OutboxEvent;
import org.bank.moneytransfer.domain.OutboxStatus;
import org.bank.moneytransfer.repository.OutboxEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WebhookOutboxWorker {
    private final OutboxEventRepository repository;
    private final MeterRegistry meterRegistry;

    public WebhookOutboxWorker(OutboxEventRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${moneytransfer.webhooks.worker-delay-ms:5000}")
    @Transactional
    public void publishDueEvents() {
        for (OutboxEvent event : repository.findTop25ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(OutboxStatus.PENDING, OffsetDateTime.now())) {
            event.markPublished();
            meterRegistry.counter("moneytransfer.webhook.delivery", "status", "published", "eventType", event.getEventType()).increment();
        }
    }
}
