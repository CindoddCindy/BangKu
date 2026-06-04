package com.bangku.transaction.service;

import com.bangku.transaction.dto.TransactionDtos;
import com.bangku.transaction.model.Transaction;
import com.bangku.transaction.repository.TransactionRepository;
import com.bangku.transaction.stream.TransactionEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private static final BigDecimal TRANSFER_FEE_RATE = new BigDecimal("0.005"); // 0.5%
    private static final BigDecimal MAX_DAILY_TRANSFER = new BigDecimal("50000000");

    private final TransactionRepository    transactionRepository;
    private final TransactionEventPublisher eventPublisher;
    private final WebClient.Builder        webClientBuilder;


    @Transactional
    public TransactionDtos.TransactionResponse transfer(TransactionDtos.TransferRequest req) {
        BigDecimal fee = req.getAmount().multiply(TRANSFER_FEE_RATE).setScale(2, RoundingMode.CEILING);
        BigDecimal total = req.getAmount().add(fee);

        // Debit from source (via account-service REST)
        debitAccount(req.getFromAccountId(), total, "TRANSFER_DEBIT");
        // Credit to destination
        creditAccount(req.getToAccountId(), req.getAmount(), "TRANSFER_CREDIT");

        Transaction tx = Transaction.builder()
                .transactionRef(UUID.randomUUID().toString())
                .fromAccountId(req.getFromAccountId())
                .toAccountId(req.getToAccountId())
                .amount(req.getAmount())
                .fee(fee)
                .transactionType(Transaction.TransactionType.TRANSFER)
                .status(Transaction.TransactionStatus.SUCCESS)
                .description(req.getDescription())
                .processedAt(LocalDateTime.now())
                .build();

        Transaction saved = transactionRepository.save(tx);
        eventPublisher.publishTransactionEvent("transaction.transfer.success", saved);
        return TransactionDtos.TransactionResponse.from(saved);
    }

    @Transactional
    public TransactionDtos.TransactionResponse deposit(TransactionDtos.DepositRequest req) {
        creditAccount(req.getToAccountId(), req.getAmount(), "DEPOSIT");
        Transaction tx = Transaction.builder()
                .transactionRef(UUID.randomUUID().toString())
                .toAccountId(req.getToAccountId())
                .amount(req.getAmount())
                .fee(BigDecimal.ZERO)
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .status(Transaction.TransactionStatus.SUCCESS)
                .description(req.getDescription())
                .processedAt(LocalDateTime.now())
                .build();
        Transaction saved = transactionRepository.save(tx);
        eventPublisher.publishTransactionEvent("transaction.deposit.success", saved);
        return TransactionDtos.TransactionResponse.from(saved);
    }

    @Transactional
    public TransactionDtos.TransactionResponse withdraw(TransactionDtos.WithdrawalRequest req) {
        debitAccount(req.getFromAccountId(), req.getAmount(), "WITHDRAWAL");
        Transaction tx = Transaction.builder()
                .transactionRef(UUID.randomUUID().toString())
                .fromAccountId(req.getFromAccountId())
                .amount(req.getAmount())
                .fee(BigDecimal.ZERO)
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .status(Transaction.TransactionStatus.SUCCESS)
                .description(req.getDescription())
                .processedAt(LocalDateTime.now())
                .build();
        Transaction saved = transactionRepository.save(tx);
        eventPublisher.publishTransactionEvent("transaction.withdrawal.success", saved);
        return TransactionDtos.TransactionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TransactionDtos.TransactionResponse> getHistory(Long accountId, int page, int size) {
        return transactionRepository.findHistoryByAccountId(accountId, size, page * size)
                .stream()
                .map(TransactionDtos.TransactionResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTransactionAnalytics(Long accountId) {
        List<Transaction> history = transactionRepository
                .findHistoryByAccountId(accountId, 1000, 0);

        // 1. Group by type → list of amounts
        Map<String, List<BigDecimal>> amountsByType = history.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getTransactionType().name(),
                        Collectors.mapping(Transaction::getAmount, Collectors.toList())
                ));

        // 2. Sum per type (using reduce inside each group)
        Map<String, BigDecimal> sumByType = amountsByType.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                ));

        // 3. Count & total fees
        Map<String, Long>       countByType = history.stream()
                .collect(Collectors.groupingBy(t -> t.getTransactionType().name(), Collectors.counting()));

        BigDecimal totalFees = history.stream()
                .map(Transaction::getFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Partition: large (>= 1M) vs small
        Map<Boolean, Long> sizePartition = history.stream()
                .collect(Collectors.partitioningBy(
                        t -> t.getAmount().compareTo(new BigDecimal("1000000")) >= 0,
                        Collectors.counting()
                ));

        // 5. Most recent 5 transaction refs (sorted + limit + map to string)
        List<String> recentRefs = history.stream()
                .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed())
                .limit(5)
                .map(Transaction::getTransactionRef)
                .collect(Collectors.toList());

        // 6. DoubleSummaryStatistics on amounts
        DoubleSummaryStatistics stats = history.stream()
                .mapToDouble(t -> t.getAmount().doubleValue())
                .summaryStatistics();

        // 7. Total credited (incoming) vs debited (outgoing)
        BigDecimal totalCredited = history.stream()
                .filter(t -> Objects.equals(t.getToAccountId(), accountId))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDebited = history.stream()
                .filter(t -> Objects.equals(t.getFromAccountId(), accountId))
                .map(t -> t.getAmount().add(t.getFee()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 8. Status distribution (peek for logging)
        Map<String, Long> statusDist = history.stream()
                .peek(t -> log.debug("Processing tx: {}", t.getTransactionRef()))
                .collect(Collectors.groupingBy(t -> t.getStatus().name(), Collectors.counting()));

        return Map.of(
                "totalTransactions", history.size(),
                "sumByType",         sumByType,
                "countByType",       countByType,
                "totalFeesPaid",     totalFees,
                "largeTransactions", sizePartition.get(true),
                "smallTransactions", sizePartition.get(false),
                "recentRefs",        recentRefs,
                "amountStats",       Map.of(
                        "min",   stats.getMin(),
                        "max",   stats.getMax(),
                        "avg",   stats.getAverage(),
                        "count", stats.getCount()
                ),
                "totalCredited",     totalCredited,
                "totalDebited",      totalDebited
        );
    }


    @Transactional(readOnly = true)
    public Map<String, Object> getFraudIndicators() {
        List<Transaction> suspects = transactionRepository.findPotentialDuplicates();

        // Group duplicates by from_account_id and count
        Map<Long, Long> duplicatesByAccount = suspects.stream()
                .filter(t -> t.getFromAccountId() != null)
                .collect(Collectors.groupingBy(Transaction::getFromAccountId, Collectors.counting()));

        // Flag accounts with > 2 potential duplicates
        List<Long> highRiskAccounts = duplicatesByAccount.entrySet().stream()
                .filter(e -> e.getValue() > 2)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());

        // Flatten all suspect transaction refs into one list
        List<String> suspectRefs = suspects.stream()
                .map(Transaction::getTransactionRef)
                .distinct()
                .collect(Collectors.toList());

        return Map.of(
                "suspectTransactionCount", suspects.size(),
                "suspectRefs",             suspectRefs,
                "duplicatesByAccount",     duplicatesByAccount,
                "highRiskAccounts",        highRiskAccounts
        );
    }


    private void debitAccount(Long accountId, BigDecimal amount, String ref) {
        webClientBuilder.build()
                .post()
                .uri("http://account-service:8081/api/v1/accounts/balance/adjust")
                .bodyValue(Map.of("accountId", accountId, "amount", amount.negate(), "reference", ref))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    private void creditAccount(Long accountId, BigDecimal amount, String ref) {
        webClientBuilder.build()
                .post()
                .uri("http://account-service:8081/api/v1/accounts/balance/adjust")
                .bodyValue(Map.of("accountId", accountId, "amount", amount, "reference", ref))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }
}
