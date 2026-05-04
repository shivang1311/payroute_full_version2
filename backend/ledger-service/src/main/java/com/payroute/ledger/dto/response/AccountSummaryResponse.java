package com.payroute.ledger.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountSummaryResponse {

    private Long accountId;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private BigDecimal totalFees;
    private BigDecimal netBalance;
    private String currency;
    private Long entryCount;
}
