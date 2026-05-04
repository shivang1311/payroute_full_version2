package com.payroute.payment.dto.client;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerPostRequest {

    private Long paymentId;
    private Long accountId;
    private String entryType;
    private BigDecimal amount;
    private String currency;
    private String narrative;
}
