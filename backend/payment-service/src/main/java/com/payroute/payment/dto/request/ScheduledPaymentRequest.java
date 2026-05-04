package com.payroute.payment.dto.request;

import com.payroute.payment.entity.ScheduleType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledPaymentRequest {

    @Size(max = 150)
    private String name;

    @NotNull(message = "Debtor account ID is required")
    private Long debtorAccountId;

    @NotNull(message = "Creditor account ID is required")
    private Long creditorAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    private String purposeCode;

    @Size(max = 500)
    private String remittanceInfo;

    @NotNull(message = "Schedule type is required")
    private ScheduleType scheduleType;

    @NotNull(message = "Start date/time is required")
    private LocalDateTime startAt;

    private LocalDateTime endAt;

    @Min(value = 1, message = "Max runs must be >= 1")
    private Integer maxRuns;

    /**
     * 4-6 digit transaction PIN. Required for CUSTOMER-initiated schedule creation.
     * Verified once at create time; subsequent automated runs do not re-prompt.
     */
    private String transactionPin;
}
