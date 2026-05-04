package com.payroute.payment.service;

import com.payroute.payment.dto.response.PaymentStatsResponse;
import com.payroute.payment.entity.PaymentStatus;
import com.payroute.payment.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentStatsService {

    private final PaymentOrderRepository paymentOrderRepository;

    public PaymentStatsResponse getStats(LocalDateTime from, LocalDateTime to) {
        List<Object[]> totalsList = paymentOrderRepository.aggregateTotals(from, to);
        long totalCount = 0L;
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (totalsList != null && !totalsList.isEmpty()) {
            Object[] row = totalsList.get(0);
            if (row != null && row.length >= 2) {
                totalCount = row[0] == null ? 0L : ((Number) row[0]).longValue();
                totalAmount = row[1] == null ? BigDecimal.ZERO : new BigDecimal(row[1].toString());
            }
        }

        // Status breakdown
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long completed = 0L;
        long failed = 0L;
        for (Object[] r : paymentOrderRepository.countByStatus(from, to)) {
            PaymentStatus s = (PaymentStatus) r[0];
            long c = ((Number) r[1]).longValue();
            byStatus.put(s.name(), c);
            if (s == PaymentStatus.COMPLETED) completed = c;
            if (s == PaymentStatus.FAILED) failed = c;
        }

        double successRate = totalCount == 0 ? 0.0 :
                BigDecimal.valueOf(completed)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP)
                        .doubleValue();
        double failureRate = totalCount == 0 ? 0.0 :
                BigDecimal.valueOf(failed)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP)
                        .doubleValue();

        Double avgTat = paymentOrderRepository.avgTatSeconds(from, to);

        // Channel breakdown
        Map<String, Long> byChannel = new LinkedHashMap<>();
        for (Object[] r : paymentOrderRepository.countByChannel(from, to)) {
            String key = r[0] == null ? "UNKNOWN" : r[0].toString();
            byChannel.put(key, ((Number) r[1]).longValue());
        }

        // Day series
        List<PaymentStatsResponse.DayPoint> byDay = new ArrayList<>();
        for (Object[] r : paymentOrderRepository.countByDay(from, to)) {
            LocalDate d = toLocalDate(r[0]);
            long c = ((Number) r[1]).longValue();
            BigDecimal amt = r[2] == null ? BigDecimal.ZERO : new BigDecimal(r[2].toString());
            byDay.add(PaymentStatsResponse.DayPoint.builder()
                    .date(d).count(c).amount(amt).build());
        }

        return PaymentStatsResponse.builder()
                .totalCount(totalCount)
                .totalAmount(totalAmount)
                .successRate(successRate)
                .failureRate(failureRate)
                .avgTatSeconds(avgTat)
                .completedCount(completed)
                .failedCount(failed)
                .byStatus(byStatus)
                .byChannel(byChannel)
                .byDay(byDay)
                .build();
    }

    private static LocalDate toLocalDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate ld) return ld;
        if (o instanceof Date d) return d.toLocalDate();
        if (o instanceof java.util.Date d) return new Date(d.getTime()).toLocalDate();
        return LocalDate.parse(o.toString());
    }
}
