package com.payroute.payment.dto.client;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntryResponse {

    private Long id;
    private String entryType;
    private BigDecimal amount;
}
