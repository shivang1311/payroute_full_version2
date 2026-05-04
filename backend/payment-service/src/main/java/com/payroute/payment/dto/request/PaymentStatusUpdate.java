package com.payroute.payment.dto.request;

import com.payroute.payment.entity.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentStatusUpdate {

    @NotNull(message = "New status is required")
    private PaymentStatus newStatus;

    private String reason;

    /** Rail used for settlement; optional, used by orchestrator for fee calculation. */
    private String rail;
}
