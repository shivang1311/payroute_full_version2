package com.payroute.compliance.service;

import com.payroute.compliance.client.PaymentServiceClient;
import com.payroute.compliance.dto.client.PaymentStatusUpdate;
import com.payroute.compliance.dto.response.HoldResponse;
import com.payroute.compliance.dto.response.PagedResponse;
import com.payroute.compliance.entity.Hold;
import com.payroute.compliance.entity.HoldStatus;
import com.payroute.compliance.exception.ResourceNotFoundException;
import com.payroute.compliance.mapper.HoldMapper;
import com.payroute.compliance.repository.HoldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HoldService {

    private final HoldRepository holdRepository;
    private final HoldMapper holdMapper;
    private final PaymentServiceClient paymentServiceClient;

    public PagedResponse<HoldResponse> getHolds(HoldStatus status, Pageable pageable) {
        Page<Hold> page;
        if (status != null) {
            page = holdRepository.findByStatus(status, pageable);
        } else {
            page = holdRepository.findAll(pageable);
        }
        return holdMapper.toPagedResponse(page);
    }

    public PagedResponse<HoldResponse> getHoldsByPayment(Long paymentId, Pageable pageable) {
        Page<Hold> page = holdRepository.findByPaymentId(paymentId, pageable);
        return holdMapper.toPagedResponse(page);
    }

    @Transactional
    public HoldResponse releaseHold(Long holdId, Long releasedBy, String releaseNotes) {
        Hold hold = holdRepository.findById(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Hold", "id", holdId));

        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new IllegalStateException("Hold is not in ACTIVE status, cannot release");
        }

        hold.setStatus(HoldStatus.RELEASED);
        hold.setReleasedBy(releasedBy);
        hold.setReleaseNotes(releaseNotes);
        hold.setReleasedAt(LocalDateTime.now());
        hold = holdRepository.save(hold);
        log.info("Hold {} released by user {}", holdId, releasedBy);

        // Resume orchestration in payment-service: it will set status VALIDATED → ROUTED → PROCESSING.
        try {
            paymentServiceClient.resumeAfterHold(hold.getPaymentId());
            log.info("Resume-after-hold called for payment {}", hold.getPaymentId());
        } catch (Exception e) {
            log.error("Failed to resume payment {} after hold release: {}",
                    hold.getPaymentId(), e.getMessage());
        }

        return holdMapper.toResponse(hold);
    }

    /**
     * Compliance analyst rejects a held payment. Marks hold REJECTED and pushes
     * the payment to FAILED with the analyst's reason as the audit trail.
     */
    @Transactional
    public HoldResponse rejectHold(Long holdId, Long rejectedBy, String rejectNotes) {
        Hold hold = holdRepository.findById(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Hold", "id", holdId));

        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new IllegalStateException("Hold is not in ACTIVE status, cannot reject");
        }

        hold.setStatus(HoldStatus.REJECTED);
        hold.setReleasedBy(rejectedBy);
        hold.setReleaseNotes(rejectNotes);
        hold.setReleasedAt(LocalDateTime.now());
        hold = holdRepository.save(hold);
        log.info("Hold {} rejected by user {}", holdId, rejectedBy);

        // Mark the payment FAILED — onPaymentFailed will fire its own audit trail.
        try {
            paymentServiceClient.updatePaymentStatus(hold.getPaymentId(),
                    PaymentStatusUpdate.builder()
                            .paymentId(hold.getPaymentId())
                            .newStatus("FAILED")
                            .reason("Compliance reject: " + rejectNotes)
                            .build());
            log.info("Payment {} marked FAILED after compliance reject", hold.getPaymentId());
        } catch (Exception e) {
            log.error("Failed to mark payment {} FAILED after reject: {}",
                    hold.getPaymentId(), e.getMessage());
        }

        return holdMapper.toResponse(hold);
    }
}
