package com.payroute.ledger.dto.response;

import com.payroute.ledger.entity.EntryType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntryResponse {

    private Long id;
    private Long paymentId;
    private Long accountId;
    private EntryType entryType;
    private BigDecimal amount;
    private String currency;
    private String narrative;
    private BigDecimal balanceAfter;
    private LocalDate entryDate;
    private LocalDateTime createdAt;
    private String createdBy;
}
