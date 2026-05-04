package com.payroute.payment.repository;

import com.payroute.payment.entity.PaymentOrder;
import com.payroute.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Page<PaymentOrder> findByStatus(PaymentStatus status, Pageable pageable);

    Page<PaymentOrder> findByInitiatedBy(String initiatedBy, Pageable pageable);

    List<PaymentOrder> findByDebtorAccountIdAndAmountAndCurrencyAndCreatedAtAfter(
            Long debtorAccountId, BigDecimal amount, String currency, LocalDateTime after);

    long countByDebtorAccountIdAndCreatedAtAfter(Long debtorAccountId, LocalDateTime after);

    Optional<PaymentOrder> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentOrder> findByReference(String reference);

    List<PaymentOrder> findTop500ByReferenceIsNull();

    /**
     * Sum of amounts for a given debtor account + payment method within a time window,
     * excluding terminal-failure / reversal states. Used by daily limit checks
     * (e.g. ₹1,00,000 per UPI account per calendar day).
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentOrder p " +
            "WHERE p.debtorAccountId = :debtorAccountId " +
            "AND p.paymentMethod = :method " +
            "AND p.createdAt BETWEEN :from AND :to " +
            "AND p.status NOT IN (com.payroute.payment.entity.PaymentStatus.FAILED, " +
            "                     com.payroute.payment.entity.PaymentStatus.VALIDATION_FAILED, " +
            "                     com.payroute.payment.entity.PaymentStatus.REVERSED)")
    BigDecimal sumByDebtorAndMethodInWindow(
            @Param("debtorAccountId") Long debtorAccountId,
            @Param("method") com.payroute.payment.entity.PaymentMethod method,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ----- Dashboard aggregations -----

    @Query("SELECT COUNT(p), COALESCE(SUM(p.amount), 0) FROM PaymentOrder p " +
            "WHERE p.createdAt BETWEEN :from AND :to")
    List<Object[]> aggregateTotals(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT p.status, COUNT(p) FROM PaymentOrder p " +
            "WHERE p.createdAt BETWEEN :from AND :to GROUP BY p.status")
    List<Object[]> countByStatus(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT AVG(TIMESTAMPDIFF(SECOND, created_at, updated_at)) " +
            "FROM payment_order " +
            "WHERE status = 'COMPLETED' AND created_at BETWEEN :from AND :to",
            nativeQuery = true)
    Double avgTatSeconds(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT DATE(created_at), COUNT(*), COALESCE(SUM(amount), 0) " +
            "FROM payment_order WHERE created_at BETWEEN :from AND :to " +
            "GROUP BY DATE(created_at) ORDER BY 1",
            nativeQuery = true)
    List<Object[]> countByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT p.initiationChannel, COUNT(p) FROM PaymentOrder p " +
            "WHERE p.createdAt BETWEEN :from AND :to GROUP BY p.initiationChannel")
    List<Object[]> countByChannel(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Aggregate count + total amount for a given set of payment IDs.
     * Used by settlement-service to compute batch totals from SETTLED rail instructions.
     */
    @Query("SELECT COUNT(p), COALESCE(SUM(p.amount), 0), MIN(p.currency) FROM PaymentOrder p " +
            "WHERE p.id IN :ids")
    List<Object[]> aggregateByIds(@Param("ids") List<Long> ids);
}
