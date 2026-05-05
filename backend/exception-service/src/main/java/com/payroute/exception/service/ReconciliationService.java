package com.payroute.exception.service;

import com.payroute.exception.client.LedgerServiceClient;
import com.payroute.exception.client.NotificationServiceClient;
import com.payroute.exception.client.RoutingServiceClient;
import com.payroute.exception.dto.client.BroadcastNotificationRequest;
import com.payroute.exception.dto.response.PagedResponse;
import com.payroute.exception.dto.response.ReconciliationResponse;
import com.payroute.exception.entity.ReconResult;
import com.payroute.exception.entity.ReconSource;
import com.payroute.exception.entity.ReconciliationRecord;
import com.payroute.exception.mapper.ReconciliationRecordMapper;
import com.payroute.exception.repository.ReconciliationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReconciliationService {

    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final ReconciliationRecordMapper reconciliationRecordMapper;
    private final RoutingServiceClient routingServiceClient;
    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;

    public PagedResponse<ReconciliationResponse> getReconciliationRecords(LocalDate reconDate,
                                                                          ReconResult result,
                                                                          Pageable pageable) {
        Page<ReconciliationRecord> page;
        if (reconDate != null) {
            page = reconciliationRecordRepository.findByReconDate(reconDate, pageable);
        } else if (result != null) {
            page = reconciliationRecordRepository.findByResult(result, pageable);
        } else {
            page = reconciliationRecordRepository.findAll(pageable);
        }
        return reconciliationRecordMapper.toPagedResponse(page);
    }

    /**
     * Reconcile SETTLED rail instructions against DEBIT ledger entries for a given date.
     * Produces MATCHED (both sides present) or UNMATCHED (only one side) records.
     */
    @Transactional
    public List<ReconciliationResponse> runReconciliation(LocalDate date) {
        log.info("Running reconciliation for date: {}", date);

        Set<Long> railSide = new HashSet<>(fetchIds(routingServiceClient.settledOn(date), "rail/settled-on"));
        Set<Long> ledgerSide = new HashSet<>(fetchIds(ledgerServiceClient.paymentIdsOn(date), "ledger/payment-ids-on"));

        Set<Long> matched = new TreeSet<>(railSide);
        matched.retainAll(ledgerSide);

        Set<Long> railOnly = new TreeSet<>(railSide);
        railOnly.removeAll(ledgerSide);

        Set<Long> ledgerOnly = new TreeSet<>(ledgerSide);
        ledgerOnly.removeAll(railSide);

        List<ReconciliationRecord> records = new ArrayList<>();

        for (Long paymentId : matched) {
            // One pairing per match: a RAIL-side record with a pointer to the ledger counterpart.
            records.add(ReconciliationRecord.builder()
                    .source(ReconSource.RAIL)
                    .referenceId(paymentId)
                    .counterpartId(paymentId) // same paymentId on both sides
                    .reconDate(date)
                    .result(ReconResult.MATCHED)
                    .currency("INR")
                    .notes("Rail SETTLED and ledger DEBIT both present for paymentId=" + paymentId)
                    .resolved(true)
                    .build());
        }

        for (Long paymentId : railOnly) {
            records.add(ReconciliationRecord.builder()
                    .source(ReconSource.RAIL)
                    .referenceId(paymentId)
                    .reconDate(date)
                    .result(ReconResult.UNMATCHED)
                    .currency("INR")
                    .notes("Rail SETTLED but no DEBIT ledger entry found for paymentId=" + paymentId)
                    .resolved(false)
                    .build());
        }

        for (Long paymentId : ledgerOnly) {
            records.add(ReconciliationRecord.builder()
                    .source(ReconSource.LEDGER)
                    .referenceId(paymentId)
                    .reconDate(date)
                    .result(ReconResult.UNMATCHED)
                    .currency("INR")
                    .notes("Ledger DEBIT present but no SETTLED rail instruction for paymentId=" + paymentId)
                    .resolved(false)
                    .build());
        }

        List<ReconciliationRecord> saved = reconciliationRecordRepository.saveAll(records);
        log.info("Reconciliation for {} complete: matched={}, railOnly={}, ledgerOnly={}, totalRecords={}",
                date, matched.size(), railOnly.size(), ledgerOnly.size(), saved.size());

        // Notify every RECONCILIATION analyst when fresh breaks land — same fan-out
        // pattern compliance uses for new holds. Skipped on a clean run.
        int newBreaks = railOnly.size() + ledgerOnly.size();
        if (newBreaks > 0) {
            try {
                notificationServiceClient.broadcast(BroadcastNotificationRequest.builder()
                        .role("RECONCILIATION")
                        .title("New Recon Breaks")
                        .message(newBreaks + " new break(s) detected during reconciliation for " + date
                                + " (" + railOnly.size() + " rail-only, " + ledgerOnly.size() + " ledger-only).")
                        .category("EXCEPTION")
                        .severity("WARNING")
                        .referenceType("RECON_RUN")
                        .referenceId((long) date.toEpochDay())
                        .build());
            } catch (Exception e) {
                log.error("Failed to broadcast new-break notification: {}", e.getMessage());
            }
        }

        return reconciliationRecordMapper.toResponseList(saved);
    }

    /**
     * EOD reconciliation scheduler — runs daily at 23:55 for the current date.
     * Cron matches Spring style: sec min hour day month dow
     */
    @Scheduled(cron = "0 55 23 * * *")
    public void runEodReconciliation() {
        LocalDate today = LocalDate.now();
        log.info("[EOD-SCHEDULE] Kicking off end-of-day reconciliation for {}", today);
        try {
            runReconciliation(today);
        } catch (Exception ex) {
            log.error("[EOD-SCHEDULE] Reconciliation failed for {}: {}", today, ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> fetchIds(ResponseEntity<Map<String, Object>> response, String source) {
        try {
            if (response == null || response.getBody() == null) return Collections.emptyList();
            Object data = response.getBody().get("data");
            if (data instanceof List<?> list) {
                List<Long> out = new ArrayList<>(list.size());
                for (Object o : list) {
                    if (o instanceof Number n) out.add(n.longValue());
                    else if (o != null) out.add(Long.parseLong(o.toString()));
                }
                return out;
            }
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Failed to fetch IDs from {}: {}", source, ex.getMessage());
            return Collections.emptyList();
        }
    }
}
