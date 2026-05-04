package com.payroute.exception.dto.request;

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
public class ReturnRequest {

    @NotNull(message = "Payment ID is required")
    private Long paymentId;

    private String reasonCode;

    private String reasonDesc;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private String currency;
}
