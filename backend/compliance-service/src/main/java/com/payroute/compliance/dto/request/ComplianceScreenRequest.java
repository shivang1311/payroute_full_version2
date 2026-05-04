package com.payroute.compliance.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceScreenRequest {

    @NotNull(message = "Payment ID is required")
    private Long paymentId;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private String currency;

    private Long debtorAccountId;

    private Long creditorAccountId;
}
