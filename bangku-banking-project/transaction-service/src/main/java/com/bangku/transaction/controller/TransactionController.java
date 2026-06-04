package com.bangku.transaction.controller;

import com.bangku.transaction.dto.TransactionDtos;
import com.bangku.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public ResponseEntity<TransactionDtos.TransactionResponse> transfer(
            @Valid @RequestBody TransactionDtos.TransferRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.transfer(req));
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionDtos.TransactionResponse> deposit(
            @Valid @RequestBody TransactionDtos.DepositRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.deposit(req));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionDtos.TransactionResponse> withdraw(
            @Valid @RequestBody TransactionDtos.WithdrawalRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.withdraw(req));
    }

    @GetMapping("/history/{accountId}")
    public ResponseEntity<List<TransactionDtos.TransactionResponse>> history(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(transactionService.getHistory(accountId, page, size));
    }

    @GetMapping("/analytics/{accountId}")
    public ResponseEntity<Map<String, Object>> analytics(@PathVariable Long accountId) {
        return ResponseEntity.ok(transactionService.getTransactionAnalytics(accountId));
    }

    @GetMapping("/fraud/indicators")
    public ResponseEntity<Map<String, Object>> fraudIndicators() {
        return ResponseEntity.ok(transactionService.getFraudIndicators());
    }
}
