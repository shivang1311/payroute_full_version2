package com.payroute.ledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One immutable line in the internal ledger. Identified by (paymentId, accountId,
 * entryType): a single payment usually generates a DEBIT on the debtor account,
 * a CREDIT on the creditor account, and a FEE on the bank's revenue account.
 * Reversals create REVERSAL rows rather than mutating the originals.
 */
@Entity
@Table(name = "ledger_entry", indexes = {
        @Index(name = "idx_ledger_payment_id", columnList = "payment_id"),
        @Index(name = "idx_ledger_account_id", columnList = "account_id"),
        @Index(name = "idx_ledger_entry_date", columnList = "entry_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private EntryType entryType;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Column(name = "narrative", length = 500)
    private String narrative;

    @Column(name = "balance_after", precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;
}
