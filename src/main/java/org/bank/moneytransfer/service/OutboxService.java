package org.bank.moneytransfer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bank.moneytransfer.domain.OutboxEvent;
import org.bank.moneytransfer.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final IdGenerator ids;
    private final String signingSecret;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper, IdGenerator ids,
                         @Value("${moneytransfer.webhooks.signing-secret}") String signingSecret) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.ids = ids;
        this.signingSecret = signingSecret;
    }

    public void emit(String eventType, String aggregateType, String aggregateId, String correlationId, Map<String, Object> data) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventType", eventType,
                    "aggregateType", aggregateType,
                    "aggregateId", aggregateId,
                    "correlationId", correlationId,
                    "data", data
            ));
            repository.save(new OutboxEvent(ids.eventId(), eventType, aggregateType, aggregateId, payload, sign(payload)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize outbox payload", e);
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Could not sign outbox payload", e);
        }
    }
}
