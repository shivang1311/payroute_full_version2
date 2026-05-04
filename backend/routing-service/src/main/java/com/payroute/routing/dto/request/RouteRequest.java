package com.payroute.routing.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to route a payment through the appropriate rail")
public class RouteRequest {

    @NotNull(message = "Payment ID is required")
    @Schema(description = "Payment ID to route", example = "1001")
    private Long paymentId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Schema(description = "Payment amount", example = "250000.00")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    @Schema(description = "Currency code", example = "INR")
    private String currency;

    @Schema(description = "Debtor account ID", example = "5001")
    private Long debtorAccountId;

    @Schema(description = "Creditor account ID", example = "5002")
    private Long creditorAccountId;

    @Schema(description = "Purpose code for the payment", example = "SALA")
    private String purposeCode;

    @Schema(description = "Channel through which payment was initiated", example = "MOBILE")
    private String channel;
}
