package com.payroute.ledger.dto.request;

import com.payroute.ledger.entity.FeeType;
import com.payroute.ledger.entity.RailType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeScheduleRequest {

    @NotBlank(message = "Product is required")
    @Size(max = 50, message = "Product must not exceed 50 characters")
    private String product;

    @NotNull(message = "Rail type is required")
    private RailType rail;

    @NotNull(message = "Fee type is required")
    private FeeType feeType;

    @NotNull(message = "Value is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Value must be greater than zero")
    @Digits(integer = 8, fraction = 4, message = "Value format invalid")
    private BigDecimal value;

    @Digits(integer = 8, fraction = 4)
    private BigDecimal minFee;

    @Digits(integer = 8, fraction = 4)
    private BigDecimal maxFee;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency;

    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}
