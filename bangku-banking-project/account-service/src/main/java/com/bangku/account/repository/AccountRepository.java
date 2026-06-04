package com.bangku.account.repository;

import com.bangku.account.model.Account;
import com.bangku.account.dto.AccountSummaryDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findByEmail(String email);

    @Query(value = """
        SELECT
            id,
            account_number    AS accountNumber,
            owner_name        AS ownerName,
            email,
            balance,
            account_type      AS accountType,
            status,
            total_outgoing    AS totalOutgoing,
            total_incoming    AS totalIncoming,
            total_debited     AS totalDebited,
            total_credited    AS totalCredited,
            total_fees_paid   AS totalFeesPaid
        FROM v_account_summary
        WHERE id = :id
        """, nativeQuery = true)
    Optional<AccountSummaryDto> findAccountSummaryById(@Param("id") Long id);

    @Query(value = """
        SELECT * FROM accounts
        WHERE status = 'ACTIVE'
        ORDER BY balance DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<Account> findTopAccountsByBalance(@Param("limit") int limit, @Param("offset") int offset);

    @Query(value = """
        SELECT * FROM accounts
        WHERE (LOWER(owner_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(email)      LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND status != 'CLOSED'
        ORDER BY owner_name
        """, nativeQuery = true)
    List<Account> searchAccounts(@Param("keyword") String keyword);

    @Modifying
    @Query(value = """
        UPDATE accounts
           SET balance    = balance + :amount,
               updated_at = NOW()
         WHERE id      = :id
           AND status  = 'ACTIVE'
           AND balance + :amount >= 0
        """, nativeQuery = true)
    int updateBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Query(value = """
        SELECT
            account_type                        AS type,
            COUNT(*)                            AS totalAccounts,
            SUM(balance)                        AS totalBalance,
            AVG(balance)                        AS avgBalance,
            MAX(balance)                        AS maxBalance
        FROM accounts
        WHERE status = 'ACTIVE'
        GROUP BY account_type
        ORDER BY totalBalance DESC
        """, nativeQuery = true)
    List<Object[]> getStatsByAccountType();

    @Query(value = """
        SELECT a.* FROM accounts a
        LEFT JOIN transactions t
               ON (t.from_account_id = a.id OR t.to_account_id = a.id)
              AND t.created_at >= NOW() - INTERVAL ':days days'
        WHERE a.status = 'ACTIVE'
          AND t.id IS NULL
        """, nativeQuery = true)
    List<Account> findDormantAccounts(@Param("days") int days);
}
