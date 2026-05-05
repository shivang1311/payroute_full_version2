package com.payroute.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Customer-facing opaque reference (e.g. {@code PRX9K3M7F2A8}).
     * Populated post-insert via {@link com.payroute.payment.util.PaymentReferenceGenerator}
     * so the value is derived from the auto-generated id.
     */
    @Column(name = "payment_reference", unique = true, length = 20)
    private String reference;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "debtor_account_id", nullable = false)
    private Long debtorAccountId;

    @Column(name = "creditor_account_id", nullable = false)
    private Long creditorAccountId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Column(name = "purpose_code")
    private String purposeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "initiation_channel")
    private InitiationChannel initiationChannel;

    /**
     * How the payer chose the beneficiary (UPI / BANK_TRANSFER / OTHER).
     * Drives per-method daily limits.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(30)")
    private PaymentStatus status;

    @Column(name = "initiated_by")
    private String initiatedBy;

    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;
}
