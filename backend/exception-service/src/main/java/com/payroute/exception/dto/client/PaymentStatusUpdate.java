package com.payroute.exception.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusUpdate {

    private Long paymentId;
    private String newStatus;
    private String reason;
}
