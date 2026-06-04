package com.bangku.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "bangku.transaction.events")
    public void onTransactionEvent(Map<String, Object> event) {
        String type = (String) event.get("eventType");
        log.info("📨 Received event: {}", type);

        switch (type) {
            case "transaction.transfer.success" -> sendTransferNotification(event);
            case "transaction.deposit.success"  -> sendDepositNotification(event);
            case "transaction.withdrawal.success" -> sendWithdrawalNotification(event);
            default -> log.warn("Unknown transaction event: {}", type);
        }
    }
    @RabbitListener(queues = "bangku.account.events")
    public void onAccountEvent(Map<String, Object> event) {
        String routingKey = (String) event.get("eventType");
        log.info("🏦 Account event: {}", routingKey);

        if ("account.created".equals(routingKey)) {
            sendWelcomeNotification(event);
        }
    }

    private void sendTransferNotification(Map<String, Object> event) {
        List<String> fields = event.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> e.getKey() + "=" + e.getValue())
                .sorted()
                .collect(Collectors.toList());

        log.info("""
                ✉️  TRANSFER NOTIFICATION
                ┌─────────────────────────────────────┐
                │  Ref   : {}
                │  From  : Account {}
                │  To    : Account {}
                │  Amount: Rp {}
                │  Fee   : Rp {}
                └─────────────────────────────────────┘
                """,
                event.get("transactionRef"),
                event.get("fromAccountId"),
                event.get("toAccountId"),
                event.get("amount"),
                event.get("fee"));
    }

    private void sendDepositNotification(Map<String, Object> event) {
        log.info("""
                ✉️  DEPOSIT NOTIFICATION
                ┌─────────────────────────────────────┐
                │  Ref    : {}
                │  To     : Account {}
                │  Amount : Rp {}
                └─────────────────────────────────────┘
                """,
                event.get("transactionRef"),
                event.get("toAccountId"),
                event.get("amount"));
    }

    private void sendWithdrawalNotification(Map<String, Object> event) {
        log.info("""
                ✉️  WITHDRAWAL NOTIFICATION
                ┌─────────────────────────────────────┐
                │  Ref    : {}
                │  From   : Account {}
                │  Amount : Rp {}
                └─────────────────────────────────────┘
                """,
                event.get("transactionRef"),
                event.get("fromAccountId"),
                event.get("amount"));
    }

    private void sendWelcomeNotification(Map<String, Object> event) {
        log.info("""
                🎉  WELCOME NEW ACCOUNT
                ┌─────────────────────────────────────┐
                │  Account : {}
                │  Owner   : {}
                └─────────────────────────────────────┘
                """,
                event.get("accountNumber"),
                event.get("ownerName"));
    }
}
