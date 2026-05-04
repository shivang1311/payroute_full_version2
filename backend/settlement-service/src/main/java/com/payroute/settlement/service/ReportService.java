package com.payroute.settlement.service;

import com.payroute.settlement.dto.request.ReportRequest;
import com.payroute.settlement.dto.response.PagedResponse;
import com.payroute.settlement.dto.response.PaymentReportResponse;
import com.payroute.settlement.entity.PaymentReport;
import com.payroute.settlement.entity.RailType;
import com.payroute.settlement.entity.ReportScope;
import com.payroute.settlement.entity.ReportStatus;
import com.payroute.settlement.entity.SettlementBatch;
import com.payroute.settlement.exception.ResourceNotFoundException;
import com.payroute.settlement.mapper.PaymentReportMapper;
import com.payroute.settlement.repository.PaymentReportRepository;
import com.payroute.settlement.repository.SettlementBatchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportService {

    private final PaymentReportRepository paymentReportRepository;
    private final PaymentReportMapper paymentReportMapper;
    private final SettlementBatchRepository settlementBatchRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public PaymentReportResponse generateReport(ReportRequest request) {
        log.info("Generating report: {} with scope: {}", request.getReportName(), request.getScope());

        PaymentReport report = paymentReportMapper.toEntity(request);
        report.setStatus(ReportStatus.GENERATING);
        report = paymentReportRepository.save(report);

        try {
            // Pull every settlement batch — small dataset in this phase, fine for in-memory aggregation
            List<SettlementBatch> batches = settlementBatchRepository.findAll();
            Map<String, Object> metrics = computeMetrics(request.getScope(), batches);
            report.setMetrics(objectMapper.writeValueAsString(metrics));
            report.setStatus(ReportStatus.COMPLETED);
            report.setGeneratedAt(LocalDateTime.now());
            log.info("Report {} generated successfully ({} batches aggregated)", report.getId(), batches.size());
        } catch (Exception e) {
            report.setStatus(ReportStatus.FAILED);
            log.error("Report {} generation failed: {}", report.getId(), e.getMessage(), e);
        }

        report = paymentReportRepository.save(report);
        return paymentReportMapper.toResponse(report);
    }

    public PagedResponse<PaymentReportResponse> getReports(Pageable pageable) {
        Page<PaymentReport> page = paymentReportRepository.findAll(pageable);
        return paymentReportMapper.toPagedResponse(page);
    }

    public PaymentReportResponse getReportById(Long id) {
        PaymentReport report = paymentReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentReport", "id", id));
        return paymentReportMapper.toResponse(report);
    }

    // --------------------- metrics computation ---------------------

    private Map<String, Object> computeMetrics(ReportScope scope, List<SettlementBatch> all) {
        // Filter the batches relevant to the chosen scope
        List<SettlementBatch> scoped = filterForScope(scope, all);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("scope", scope.name());
        metrics.put("generatedAt", LocalDateTime.now().toString());
        metrics.put("batchCount", scoped.size());

        // Top-line aggregates
        long totalPayments = scoped.stream().mapToLong(b -> b.getTotalCount() != null ? b.getTotalCount() : 0).sum();
        BigDecimal totalAmount = sum(scoped, SettlementBatch::getTotalAmount);
        BigDecimal totalFees = sum(scoped, SettlementBatch::getTotalFees);
        BigDecimal netAmount = sum(scoped, SettlementBatch::getNetAmount);
        BigDecimal avgAmount = totalPayments > 0
                ? totalAmount.divide(BigDecimal.valueOf(totalPayments), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        metrics.put("totalPayments", totalPayments);
        metrics.put("totalAmount", totalAmount);
        metrics.put("totalFees", totalFees);
        metrics.put("netAmount", netAmount);
        metrics.put("averagePaymentAmount", avgAmount);

        // Scope-specific breakdowns
        switch (scope) {
            case RAIL -> metrics.put("railBreakdown", railBreakdown(scoped));
            case PERIOD, MONTHLY -> metrics.put("monthlyBreakdown", monthlyBreakdown(scoped));
            case DAILY -> metrics.put("dailyBreakdown", dailyBreakdown(scoped));
            case PRODUCT, CUSTOM -> {
                // Include all breakdowns for a product-level / custom view
                metrics.put("railBreakdown", railBreakdown(scoped));
                metrics.put("monthlyBreakdown", monthlyBreakdown(scoped));
            }
        }

        return metrics;
    }

    private List<SettlementBatch> filterForScope(ReportScope scope, List<SettlementBatch> all) {
        LocalDate today = LocalDate.now();
        return switch (scope) {
            case DAILY -> all.stream()
                    .filter(b -> b.getCreatedAt() != null && b.getCreatedAt().toLocalDate().equals(today))
                    .toList();
            case MONTHLY -> {
                YearMonth thisMonth = YearMonth.from(today);
                yield all.stream()
                        .filter(b -> b.getCreatedAt() != null
                                && YearMonth.from(b.getCreatedAt()).equals(thisMonth))
                        .toList();
            }
            // PRODUCT / RAIL / PERIOD / CUSTOM → aggregate across everything we have
            default -> all;
        };
    }

    private Map<String, Object> railBreakdown(List<SettlementBatch> batches) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (RailType rail : RailType.values()) {
            List<SettlementBatch> rb = batches.stream()
                    .filter(b -> b.getRail() == rail)
                    .toList();
            if (rb.isEmpty()) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("batchCount", rb.size());
            entry.put("paymentCount",
                    rb.stream().mapToLong(b -> b.getTotalCount() != null ? b.getTotalCount() : 0).sum());
            entry.put("totalAmount", sum(rb, SettlementBatch::getTotalAmount));
            entry.put("totalFees", sum(rb, SettlementBatch::getTotalFees));
            entry.put("netAmount", sum(rb, SettlementBatch::getNetAmount));
            out.put(rail.name(), entry);
        }
        return out;
    }

    private Map<String, Object> monthlyBreakdown(List<SettlementBatch> batches) {
        DateTimeFormatter ym = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, List<SettlementBatch>> grouped = batches.stream()
                .filter(b -> b.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getCreatedAt().format(ym),
                        LinkedHashMap::new,
                        Collectors.toList()));

        Map<String, Object> out = new LinkedHashMap<>();
        grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("batchCount", e.getValue().size());
                    entry.put("paymentCount",
                            e.getValue().stream().mapToLong(b -> b.getTotalCount() != null ? b.getTotalCount() : 0).sum());
                    entry.put("totalAmount", sum(e.getValue(), SettlementBatch::getTotalAmount));
                    out.put(e.getKey(), entry);
                });
        return out;
    }

    private Map<String, Object> dailyBreakdown(List<SettlementBatch> batches) {
        DateTimeFormatter d = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, List<SettlementBatch>> grouped = batches.stream()
                .filter(b -> b.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getCreatedAt().format(d),
                        LinkedHashMap::new,
                        Collectors.toList()));

        Map<String, Object> out = new LinkedHashMap<>();
        grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("batchCount", e.getValue().size());
                    entry.put("paymentCount",
                            e.getValue().stream().mapToLong(b -> b.getTotalCount() != null ? b.getTotalCount() : 0).sum());
                    entry.put("totalAmount", sum(e.getValue(), SettlementBatch::getTotalAmount));
                    out.put(e.getKey(), entry);
                });
        return out;
    }

    private BigDecimal sum(List<SettlementBatch> batches, java.util.function.Function<SettlementBatch, BigDecimal> fn) {
        return batches.stream()
                .map(fn)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
