package com.bangku.transaction.stream;

import com.bangku.transaction.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventPublisher {

    public static final String EXCHANGE = "bangku.events";

    private final RabbitTemplate rabbitTemplate;

    public void publishTransactionEvent(String routingKey, Transaction tx) {
        Map<String, Object> event = Map.of(
                "eventType",      routingKey,
                "transactionRef", tx.getTransactionRef(),
                "amount",         tx.getAmount(),
                "fee",            tx.getFee(),
                "type",           tx.getTransactionType().name(),
                "status",         tx.getStatus().name(),
                "fromAccountId",  tx.getFromAccountId() != null ? tx.getFromAccountId() : "N/A",
                "toAccountId",    tx.getToAccountId()   != null ? tx.getToAccountId()   : "N/A",
                "processedAt",    tx.getProcessedAt() != null ? tx.getProcessedAt().toString() : LocalDateTime.now().toString()
        );
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, routingKey, event);
            log.info("Published event [{}] for tx: {}", routingKey, tx.getTransactionRef());
        } catch (Exception e) {
            log.warn("Failed to publish event: {}", e.getMessage());
        }
    }
}
