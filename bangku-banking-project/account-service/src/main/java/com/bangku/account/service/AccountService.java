package com.bangku.account.service;

import com.bangku.account.config.AppConfig;
import com.bangku.account.dto.AccountDtos;
import com.bangku.account.dto.AccountSummaryDto;
import com.bangku.account.model.Account;
import com.bangku.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final RabbitTemplate    rabbitTemplate;


    public AccountDtos.AccountResponse createAccount(AccountDtos.CreateAccountRequest req) {
        String accountNumber = generateAccountNumber();
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .ownerName(req.getOwnerName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .balance(req.getInitialDeposit())
                .accountType(req.getAccountType())
                .status(Account.AccountStatus.ACTIVE)
                .build();

        Account saved = accountRepository.save(account);
        publishEvent("account.created", Map.of(
                "accountId", saved.getId(),
                "accountNumber", saved.getAccountNumber(),
                "ownerName", saved.getOwnerName()
        ));
        return AccountDtos.AccountResponse.from(saved);
    }


    @Cacheable(value = "accounts", key = "#id")
    @Transactional(readOnly = true)
    public AccountDtos.AccountResponse getById(Long id) {
        return accountRepository.findById(id)
                .map(AccountDtos.AccountResponse::from)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<AccountSummaryDto> getAccountSummary(Long id) {
        return accountRepository.findAccountSummaryById(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPortfolioAnalytics() {
        List<Account> all = accountRepository.findAll();

        // 1. Filter only active accounts
        List<Account> active = all.stream()
                .filter(a -> a.getStatus() == Account.AccountStatus.ACTIVE)
                .collect(Collectors.toList());

        // 2. Group by account type → total balance per type
        Map<String, BigDecimal> balanceByType = active.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getAccountType().name(),
                        Collectors.reducing(BigDecimal.ZERO,
                                Account::getBalance,
                                BigDecimal::add)
                ));

        // 3. DoubleSummaryStatistics for balance distribution
        DoubleSummaryStatistics balanceStats = active.stream()
                .mapToDouble(a -> a.getBalance().doubleValue())
                .summaryStatistics();

        // 4. Partition into high-value (>= 10M) vs regular
        Map<Boolean, List<String>> partitioned = active.stream()
                .collect(Collectors.partitioningBy(
                        a -> a.getBalance().compareTo(new BigDecimal("10000000")) >= 0,
                        Collectors.mapping(Account::getOwnerName, Collectors.toList())
                ));

        // 5. Count per status (all accounts)
        Map<String, Long> countByStatus = all.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getStatus().name(),
                        Collectors.counting()
                ));

        // 6. Top 3 balances — sorted + limited
        List<Map<String, Object>> top3 = active.stream()
                .sorted(Comparator.comparing(Account::getBalance).reversed())
                .limit(3)
                .map(a -> Map.<String, Object>of(
                        "name",    a.getOwnerName(),
                        "balance", a.getBalance(),
                        "type",    a.getAccountType().name()
                ))
                .collect(Collectors.toList());

        // 7. Distinct account types in use
        List<String> typesInUse = active.stream()
                .map(a -> a.getAccountType().name())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // 8. Comma-joined owner names (joining collector)
        String ownerList = active.stream()
                .map(Account::getOwnerName)
                .sorted()
                .collect(Collectors.joining(", "));

        // 9. Average balance per type (toMap variant)
        Map<String, Double> avgBalanceByType = active.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getAccountType().name(),
                        Collectors.averagingDouble(a -> a.getBalance().doubleValue())
                ));

        // 10. Total balance (reduce)
        BigDecimal totalBalance = active.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "totalAccounts",     all.size(),
                "activeAccounts",    active.size(),
                "totalBalance",      totalBalance,
                "balanceByType",     balanceByType,
                "avgBalanceByType",  avgBalanceByType,
                "highValueOwners",   partitioned.get(true),
                "regularOwners",     partitioned.get(false),
                "top3ByBalance",     top3,
                "countByStatus",     countByStatus,
                "typesInUse",        typesInUse
        );
    }

    @Transactional(readOnly = true)
    public List<AccountDtos.AccountResponse> searchAccounts(String keyword, int page, int size) {
        List<Account> raw = (keyword != null && !keyword.isBlank())
                ? accountRepository.searchAccounts(keyword)
                : accountRepository.findAll();

        return raw.stream()
                .filter(a -> a.getStatus() != Account.AccountStatus.CLOSED)
                .sorted(Comparator.comparing(Account::getOwnerName))
                .skip((long) page * size)
                .limit(size)
                .map(AccountDtos.AccountResponse::from)
                .collect(Collectors.toList());
    }


    @CacheEvict(value = "accounts", key = "#req.accountId")
    public boolean adjustBalance(AccountDtos.BalanceAdjustRequest req) {
        int updated = accountRepository.updateBalance(req.getAccountId(), req.getAmount());
        if (updated > 0) {
            publishEvent("account.balance.updated", Map.of(
                    "accountId", req.getAccountId(),
                    "amount",    req.getAmount(),
                    "reference", req.getReference()
            ));
        }
        return updated > 0;
    }


    private String generateAccountNumber() {
        String year = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy"));
        String seq  = String.format("%04d", (int)(Math.random() * 9999));
        return "BK-" + seq + "-" + year;
    }

    private void publishEvent(String routingKey, Object payload) {
        try {
            rabbitTemplate.convertAndSend(AppConfig.EXCHANGE_BANGKU, routingKey, payload);
        } catch (Exception e) {
            log.warn("Failed to publish event [{}]: {}", routingKey, e.getMessage());
        }
    }
}
