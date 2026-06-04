package com.bangku.transaction.repository;

import com.bangku.transaction.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByTransactionRef(String ref);

    @Query(value = """
        SELECT t.*
        FROM transactions t
        WHERE (t.from_account_id = :accountId OR t.to_account_id = :accountId)
          AND t.status != 'PENDING'
        ORDER BY t.created_at DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<Transaction> findHistoryByAccountId(
            @Param("accountId") Long accountId,
            @Param("limit")     int limit,
            @Param("offset")    int offset);

    @Query(value = """
        WITH monthly AS (
            SELECT
                DATE_TRUNC('month', created_at)     AS month,
                transaction_type,
                COUNT(*)                            AS total_count,
                SUM(amount)                         AS total_amount,
                AVG(amount)                         AS avg_amount,
                SUM(fee)                            AS total_fees
            FROM transactions
            WHERE status = 'SUCCESS'
              AND created_at >= :fromDate
            GROUP BY DATE_TRUNC('month', created_at), transaction_type
        )
        SELECT * FROM monthly ORDER BY month DESC, total_amount DESC
        """, nativeQuery = true)
    List<Object[]> getMonthlySummary(@Param("fromDate") LocalDateTime fromDate);

    @Query(value = """
        SELECT t1.*
        FROM transactions t1
        JOIN transactions t2
          ON  t1.id               != t2.id
          AND t1.from_account_id   = t2.from_account_id
          AND t1.to_account_id     = t2.to_account_id
          AND t1.amount            = t2.amount
          AND ABS(EXTRACT(EPOCH FROM (t1.created_at - t2.created_at))) < 60
        WHERE t1.created_at >= NOW() - INTERVAL '1 hour'
        """, nativeQuery = true)
    List<Transaction> findPotentialDuplicates();

    @Query(value = """
        SELECT * FROM transactions
        WHERE status    = 'SUCCESS'
          AND created_at BETWEEN :from AND :to
        ORDER BY amount DESC
        LIMIT :topN
        """, nativeQuery = true)
    List<Transaction> findTopByAmountInRange(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to,
            @Param("topN") int topN);

    @Modifying
    @Query(value = """
        UPDATE transactions
           SET status       = :newStatus,
               processed_at = NOW()
         WHERE id     = :id
           AND status = 'PENDING'
        """, nativeQuery = true)
    int updateStatus(@Param("id") Long id, @Param("newStatus") String newStatus);

    @Query(value = """
        SELECT
            COALESCE(SUM(amount), 0)  AS total_volume,
            COUNT(*)                  AS total_count
        FROM transactions
        WHERE from_account_id = :accountId
          AND status         = 'SUCCESS'
          AND created_at     >= NOW() - INTERVAL '7 days'
        """, nativeQuery = true)
    Object[] getRolling7DayVolume(@Param("accountId") Long accountId);
}
