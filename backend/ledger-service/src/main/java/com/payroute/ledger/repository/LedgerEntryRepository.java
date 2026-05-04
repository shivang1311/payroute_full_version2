package com.payroute.ledger.repository;

import com.payroute.ledger.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByPaymentId(Long paymentId);

    Page<LedgerEntry> findByAccountId(Long accountId, Pageable pageable);

    List<LedgerEntry> findByEntryDateBetween(LocalDate startDate, LocalDate endDate);

    Page<LedgerEntry> findByPaymentIdAndAccountId(Long paymentId, Long accountId, Pageable pageable);

    Page<LedgerEntry> findByPaymentId(Long paymentId, Pageable pageable);

    Page<LedgerEntry> findByEntryDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<LedgerEntry> findByAccountId(Long accountId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e " +
            "WHERE e.entryType = com.payroute.ledger.entity.EntryType.FEE " +
            "AND e.entryDate BETWEEN :from AND :to")
    BigDecimal feeIncomeTotal(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Total FEE entries booked against a given list of payment IDs.
     * Used by settlement-service to compute total fees for a settlement batch.
     */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e " +
            "WHERE e.entryType = com.payroute.ledger.entity.EntryType.FEE " +
            "AND e.paymentId IN :ids")
    BigDecimal feeTotalForPayments(@Param("ids") List<Long> ids);

    /**
     * Distinct payment IDs with a DEBIT entry posted on a given date.
     * Used by exception-service reconciliation to build the ledger-side set.
     */
    @Query("SELECT DISTINCT e.paymentId FROM LedgerEntry e " +
            "WHERE e.entryType = com.payroute.ledger.entity.EntryType.DEBIT " +
            "AND e.entryDate = :date")
    List<Long> findDebitPaymentIdsOnDate(@Param("date") LocalDate date);

    @Query(value = "SELECT entry_date, COALESCE(SUM(amount),0) FROM ledger_entry " +
            "WHERE entry_type = 'FEE' AND entry_date BETWEEN :from AND :to " +
            "GROUP BY entry_date ORDER BY entry_date",
            nativeQuery = true)
    List<Object[]> feeIncomeByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // --- Statement queries ---

    @Query("SELECT e FROM LedgerEntry e WHERE e.accountId = :accountId " +
            "AND e.entryDate BETWEEN :from AND :to " +
            "ORDER BY e.entryDate ASC, e.id ASC")
    List<LedgerEntry> findStatementEntries(@Param("accountId") Long accountId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /**
     * Net balance (credits - debits) for an account for entries strictly BEFORE a date.
     * Used as the opening balance when rendering a statement.
     */
    @Query("SELECT COALESCE(SUM(CASE " +
            "WHEN e.entryType = com.payroute.ledger.entity.EntryType.CREDIT THEN e.amount " +
            "WHEN e.entryType = com.payroute.ledger.entity.EntryType.DEBIT THEN -e.amount " +
            "WHEN e.entryType = com.payroute.ledger.entity.EntryType.FEE THEN -e.amount " +
            "WHEN e.entryType = com.payroute.ledger.entity.EntryType.TAX THEN -e.amount " +
            "WHEN e.entryType = com.payroute.ledger.entity.EntryType.REVERSAL THEN e.amount " +
            "ELSE 0 END), 0) " +
            "FROM LedgerEntry e WHERE e.accountId = :accountId AND e.entryDate < :asOf")
    BigDecimal netBalanceBefore(@Param("accountId") Long accountId, @Param("asOf") LocalDate asOf);
}
