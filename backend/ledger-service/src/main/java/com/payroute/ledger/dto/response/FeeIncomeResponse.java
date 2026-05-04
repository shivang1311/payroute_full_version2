package com.payroute.ledger.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeIncomeResponse {

    private BigDecimal total;
    private List<DayPoint> byDay;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayPoint {
        private LocalDate date;
        private BigDecimal amount;
    }
}
