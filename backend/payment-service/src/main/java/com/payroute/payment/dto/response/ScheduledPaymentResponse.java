package com.payroute.payment.dto.response;

import com.payroute.payment.entity.ScheduleType;
import com.payroute.payment.entity.ScheduledPaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledPaymentResponse {
    private Long id;
    private Long userId;
    private String name;
    private Long debtorAccountId;
    private Long creditorAccountId;
    private BigDecimal amount;
    private String currency;
    private String purposeCode;
    private String remittanceInfo;
    private ScheduleType scheduleType;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer maxRuns;
    private Integer runsCount;
    private LocalDateTime nextRunAt;
    private LocalDateTime lastRunAt;
    private Long lastPaymentId;
    private String lastError;
    private ScheduledPaymentStatus status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
