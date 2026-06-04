package com.bangku.account.dto;

import java.math.BigDecimal;
public interface AccountSummaryDto {
    Long         getId();
    String       getAccountNumber();
    String       getOwnerName();
    String       getEmail();
    BigDecimal   getBalance();
    String       getAccountType();
    String       getStatus();
    Long         getTotalOutgoing();
    Long         getTotalIncoming();
    BigDecimal   getTotalDebited();
    BigDecimal   getTotalCredited();
    BigDecimal   getTotalFeesPaid();
}
