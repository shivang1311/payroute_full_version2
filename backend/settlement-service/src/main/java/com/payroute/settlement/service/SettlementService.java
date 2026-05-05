package com.payroute.settlement.service;

import com.payroute.settlement.client.LedgerServiceClient;
import com.payroute.settlement.client.NotificationServiceClient;
import com.payroute.settlement.client.PaymentServiceClient;
import com.payroute.settlement.client.RoutingServiceClient;
import com.payroute.settlement.dto.client.BroadcastNotificationRequest;
import com.payroute.settlement.dto.request.SettlementBatchRequest;
import com.payroute.settlement.dto.response.PagedResponse;
import com.payroute.settlement.dto.response.SettlementBatchResponse;
import com.payroute.settlement.entity.BatchStatus;
import com.payroute.settlement.entity.RailType;
import com.payroute.settlement.entity.SettlementBatch;
import com.payroute.settlement.exception.ResourceNotFoundException;
import com.payroute.settlement.mapper.SettlementBatchMapper;
import com.payroute.settlement.repository.SettlementBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementBatchMapper settlementBatchMapper;
    private final RoutingServiceClient routingServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;

    @Transactional
    public SettlementBatchResponse createBatch(SettlementBatchRequest request) {
        log.info("Creating settlement batch for rail {} from {} to {}",
                request.getRail(), request.getPeriodStart(), request.getPeriodEnd());

        SettlementBatch batch = settlementBatchMapper.toEntity(request);
        batch.setStatus(BatchStatus.PENDING);

        // 1. Get SETTLED payment IDs for this rail + period from routing-service
        List<Long> paymentIds = fetchSettlementEligibleIds(
                request.getRail().name(), request.getPeriodStart(), request.getPeriodEnd());
        log.info("Rail {} period {}..{} matched {} settled payment IDs",
                request.getRail(), request.getPeriodStart(), request.getPeriodEnd(), paymentIds.size());

        long count = 0L;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        String currency = "INR";
        String failureReason = null;

        if (!paymentIds.isEmpty()) {
            // 2. Aggregate payment totals (count + sum amount + currency) from payment-service
            try {
                Map<String, Object> agg = unwrapData(paymentServiceClient.aggregateByIds(paymentIds));
                if (agg != null) {
                    if (agg.get("count") != null) count = ((Number) agg.get("count")).longValue();
                    if (agg.get("totalAmount") != null) totalAmount = new BigDecimal(agg.get("totalAmount").toString());
                    if (agg.get("currency") != null) currency = agg.get("currency").toString();
                }
            } catch (Exception e) {
                log.error("Settlement batch {} failed: payment-service aggregation error: {}",
                        request.getRail(), e.getMessage());
                failureReason = "payment aggregation: " + e.getMessage();
            }

            // 3. Get total fees for these payments from ledger-service
            if (failureReason == null) {
                try {
                    Map<String, Object> feeResp = unwrapData(ledgerServiceClient.feesForPayments(paymentIds));
                    if (feeResp != null && feeResp.get("total") != null) {
                        totalFees = new BigDecimal(feeResp.get("total").toString());
                    }
                } catch (Exception e) {
                    log.error("Settlement batch {} failed: ledger-service fees error: {}",
                            request.getRail(), e.getMessage());
                    failureReason = "fee lookup: " + e.getMessage();
                }
            }
        }

        BigDecimal netAmount = totalAmount.subtract(totalFees).setScale(2, RoundingMode.HALF_UP);

        batch.setTotalCount((int) count);
        batch.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        batch.setTotalFees(totalFees.setScale(2, RoundingMode.HALF_UP));
        batch.setNetAmount(netAmount);
        batch.setCurrency(currency);

        // If we couldn't pull totals from a downstream service, the batch can't be
        // posted/reconciled — flip to FAILED and notify recon analysts.
        if (failureReason != null) {
            batch.setStatus(BatchStatus.FAILED);
        }

        batch = settlementBatchRepository.save(batch);
        log.info("Settlement batch {} created: rail={} status={} count={} gross={} fees={} net={} currency={}",
                batch.getId(), batch.getRail(), batch.getStatus(), count, batch.getTotalAmount(),
                batch.getTotalFees(), batch.getNetAmount(), currency);

        if (batch.getStatus() == BatchStatus.FAILED) {
            try {
                notificationServiceClient.broadcast(BroadcastNotificationRequest.builder()
                        .role("RECONCILIATION")
                        .title("Settlement Batch Failed")
                        .message("Batch #" + batch.getId() + " (" + batch.getRail()
                                + ") could not be built — " + failureReason + ". Investigate before cycle close.")
                        .category("SETTLEMENT")
                        .severity("ERROR")
                        .referenceType("SETTLEMENT_BATCH")
                        .referenceId(batch.getId())
                        .build());
            } catch (Exception e) {
                log.error("Failed to broadcast batch-failed notification: {}", e.getMessage());
            }
        }

        return settlementBatchMapper.toResponse(batch);
    }

    @SuppressWarnings("unchecked")
    private List<Long> fetchSettlementEligibleIds(String rail,
                                                  java.time.LocalDateTime from,
                                                  java.time.LocalDateTime to) {
        try {
            Map<String, Object> resp = unwrapData(routingServiceClient.settlementEligible(rail, from, to));
            if (resp == null) return Collections.emptyList();
            // routing-service returns ApiResponse.success(List<Long>) → data is a List
            Object data = resp.get("_raw_data");
            if (data instanceof List<?> list) {
                return list.stream()
                        .map(o -> o instanceof Number n ? n.longValue() : Long.parseLong(o.toString()))
                        .toList();
            }
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Failed to fetch settlement-eligible payment IDs for rail {}: {}", rail, ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Unwrap ApiResponse envelope: {success, data, message, ...} → data as Map.
     * For List payloads, stores them under "_raw_data" key so caller can retrieve them.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapData(ResponseEntity<Map<String, Object>> response) {
        if (response == null || response.getBody() == null) return null;
        Map<String, Object> body = response.getBody();
        Object data = body.get("data");
        if (data instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (data instanceof List<?>) {
            java.util.HashMap<String, Object> wrapped = new java.util.HashMap<>();
            wrapped.put("_raw_data", data);
            return wrapped;
        }
        return body; // fallback: caller can read top-level keys
    }

    public PagedResponse<SettlementBatchResponse> getBatches(RailType rail, BatchStatus status, Pageable pageable) {
        Page<SettlementBatch> page;
        if (rail != null && status != null) {
            page = settlementBatchRepository.findByRailAndStatus(rail, status, pageable);
        } else if (rail != null) {
            page = settlementBatchRepository.findByRail(rail, pageable);
        } else if (status != null) {
            page = settlementBatchRepository.findByStatus(status, pageable);
        } else {
            page = settlementBatchRepository.findAll(pageable);
        }
        return settlementBatchMapper.toPagedResponse(page);
    }

    public SettlementBatchResponse getBatchById(Long id) {
        SettlementBatch batch = settlementBatchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SettlementBatch", "id", id));
        return settlementBatchMapper.toResponse(batch);
    }
}
