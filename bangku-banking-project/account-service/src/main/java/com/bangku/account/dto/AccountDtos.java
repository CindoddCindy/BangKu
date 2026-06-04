package com.bangku.account.dto;

import com.bangku.account.model.Account;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AccountDtos {

    @Data
    public static class CreateAccountRequest {
        @NotBlank(message = "Owner name is required")
        @Size(max = 100)
        private String ownerName;

        @NotBlank @Email(message = "Valid email required")
        private String email;

        @Pattern(regexp = "^[0-9]{10,15}$", message = "Valid phone number required")
        private String phone;

        @NotNull
        private Account.AccountType accountType;

        @DecimalMin(value = "0.00")
        private BigDecimal initialDeposit = BigDecimal.ZERO;
    }

    @Data
    public static class UpdateAccountRequest {
        @Size(max = 100)
        private String ownerName;

        @Pattern(regexp = "^[0-9]{10,15}$")
        private String phone;

        private Account.AccountStatus status;
    }

    @Data
    public static class AccountResponse {
        private Long             id;
        private String           accountNumber;
        private String           ownerName;
        private String           email;
        private String           phone;
        private BigDecimal       balance;
        private String           accountType;
        private String           status;
        private LocalDateTime    createdAt;

        public static AccountResponse from(Account a) {
            AccountResponse r = new AccountResponse();
            r.id            = a.getId();
            r.accountNumber = a.getAccountNumber();
            r.ownerName     = a.getOwnerName();
            r.email         = a.getEmail();
            r.phone         = a.getPhone();
            r.balance       = a.getBalance();
            r.accountType   = a.getAccountType().name();
            r.status        = a.getStatus().name();
            r.createdAt     = a.getCreatedAt();
            return r;
        }
    }

    @Data
    public static class BalanceAdjustRequest {
        @NotNull private Long       accountId;
        @NotNull private BigDecimal amount;   // positive = credit, negative = debit
        @NotBlank private String    reference;
    }
}
