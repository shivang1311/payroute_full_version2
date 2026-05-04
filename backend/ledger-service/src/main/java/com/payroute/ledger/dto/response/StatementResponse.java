package com.payroute.ledger.dto.response;

import com.payroute.ledger.entity.EntryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatementResponse {

    private Long accountId;
    private String currency;
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private Integer entryCount;
    private List<StatementLine> entries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatementLine {
        private Long entryId;
        private Long paymentId;
        private LocalDate entryDate;
        private LocalDateTime postedAt;
        private EntryType entryType;
        private BigDecimal debit;
        private BigDecimal credit;
        private String currency;
        private String narrative;
        private BigDecimal runningBalance;
    }
}
