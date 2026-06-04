package com.bangku.transaction.dto;

import com.bangku.transaction.model.Transaction;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionDtos {

    @Data
    public static class TransferRequest {
        @NotNull private Long fromAccountId;
        @NotNull private Long toAccountId;
        @NotNull @DecimalMin("1.00") private BigDecimal amount;
        private String description;
    }

    @Data
    public static class DepositRequest {
        @NotNull private Long toAccountId;
        @NotNull @DecimalMin("1.00") private BigDecimal amount;
        private String description;
    }

    @Data
    public static class WithdrawalRequest {
        @NotNull private Long fromAccountId;
        @NotNull @DecimalMin("1.00") private BigDecimal amount;
        private String description;
    }

    @Data
    public static class TransactionResponse {
        private Long             id;
        private String           transactionRef;
        private Long             fromAccountId;
        private Long             toAccountId;
        private BigDecimal       amount;
        private BigDecimal       fee;
        private String           type;
        private String           status;
        private String           description;
        private LocalDateTime    createdAt;
        private LocalDateTime    processedAt;

        public static TransactionResponse from(Transaction t) {
            TransactionResponse r = new TransactionResponse();
            r.id             = t.getId();
            r.transactionRef = t.getTransactionRef();
            r.fromAccountId  = t.getFromAccountId();
            r.toAccountId    = t.getToAccountId();
            r.amount         = t.getAmount();
            r.fee            = t.getFee();
            r.type           = t.getTransactionType().name();
            r.status         = t.getStatus().name();
            r.description    = t.getDescription();
            r.createdAt      = t.getCreatedAt();
            r.processedAt    = t.getProcessedAt();
            return r;
        }
    }
}
