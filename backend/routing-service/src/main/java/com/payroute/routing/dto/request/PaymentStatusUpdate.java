package com.payroute.routing.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment status update request sent to payment-service")
public class PaymentStatusUpdate {

    @NotNull(message = "Payment ID is required")
    @Schema(description = "Payment ID", example = "1001")
    private Long paymentId;

    @NotNull(message = "New status is required")
    @Schema(description = "New payment status", example = "COMPLETED")
    private String newStatus;

    @Schema(description = "Reason for status change", example = "Rail settlement completed")
    private String reason;

    @Schema(description = "Rail used for settlement (for fee calculation)", example = "NEFT")
    private String rail;
}
