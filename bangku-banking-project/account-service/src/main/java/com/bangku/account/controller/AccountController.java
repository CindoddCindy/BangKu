package com.bangku.account.controller;

import com.bangku.account.dto.AccountDtos;
import com.bangku.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountDtos.AccountResponse> create(
            @Valid @RequestBody AccountDtos.CreateAccountRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountDtos.AccountResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getById(id));
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<?> getSummary(@PathVariable Long id) {
        return accountService.getAccountSummary(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<List<AccountDtos.AccountResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(accountService.searchAccounts(keyword, page, size));
    }

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> analytics() {
        return ResponseEntity.ok(accountService.getPortfolioAnalytics());
    }

    @PostMapping("/balance/adjust")
    public ResponseEntity<Map<String, Object>> adjustBalance(
            @Valid @RequestBody AccountDtos.BalanceAdjustRequest req) {
        boolean ok = accountService.adjustBalance(req);
        return ok
                ? ResponseEntity.ok(Map.of("success", true,  "message", "Balance updated"))
                : ResponseEntity.badRequest().body(Map.of("success", false, "message", "Update failed — insufficient funds or account not active"));
    }
}
