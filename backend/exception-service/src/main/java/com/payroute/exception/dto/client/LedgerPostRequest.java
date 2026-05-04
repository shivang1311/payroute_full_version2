package com.payroute.exception.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerPostRequest {

    private Long accountId;
    private Long paymentId;
    private String entryType;
    private BigDecimal amount;
    private String currency;
    private String description;
}
