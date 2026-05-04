package com.payroute.settlement.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryResponse {

    private Long id;
    private Long accountId;
    private Long paymentId;
    private String entryType;
    private BigDecimal amount;
    private String currency;
    private BigDecimal balanceAfter;
    private String description;
    private LocalDateTime entryDate;
    private LocalDateTime createdAt;
}
