package com.payroute.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatsResponse {

    private long totalCount;
    private BigDecimal totalAmount;
    private double successRate;   // 0-100
    private double failureRate;   // 0-100
    private Double avgTatSeconds; // null if no COMPLETED rows
    private long completedCount;
    private long failedCount;

    private Map<String, Long> byStatus;
    private Map<String, Long> byChannel;
    private List<DayPoint> byDay;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayPoint {
        private LocalDate date;
        private long count;
        private BigDecimal amount;
    }
}
