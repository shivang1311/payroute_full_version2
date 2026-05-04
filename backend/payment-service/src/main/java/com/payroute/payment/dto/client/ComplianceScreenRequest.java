package com.payroute.payment.dto.client;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplianceScreenRequest {

    private Long paymentId;
    private BigDecimal amount;
    private String currency;
    private Long debtorAccountId;
    private Long creditorAccountId;
}
