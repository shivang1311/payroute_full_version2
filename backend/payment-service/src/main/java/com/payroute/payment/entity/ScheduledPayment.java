package com.payroute.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "scheduled_payment", indexes = {
        @Index(name = "idx_sched_user", columnList = "user_id"),
        @Index(name = "idx_sched_status_next", columnList = "status, next_run_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "debtor_account_id", nullable = false)
    private Long debtorAccountId;

    @Column(name = "creditor_account_id", nullable = false)
    private Long creditorAccountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "purpose_code", length = 40)
    private String purposeCode;

    @Column(name = "remittance_info", length = 500)
    private String remittanceInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 20)
    private ScheduleType scheduleType;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "max_runs")
    private Integer maxRuns;

    @Column(name = "runs_count", nullable = false)
    @Builder.Default
    private Integer runsCount = 0;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "last_payment_id")
    private Long lastPaymentId;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScheduledPaymentStatus status;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
