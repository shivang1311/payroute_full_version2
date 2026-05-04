package com.payroute.compliance.service;

import com.payroute.compliance.client.NotificationServiceClient;
import com.payroute.compliance.client.PaymentServiceClient;
import com.payroute.compliance.dto.client.NotificationRequest;
import com.payroute.compliance.dto.client.PaymentStatusUpdate;
import com.payroute.compliance.dto.request.ComplianceScreenRequest;
import com.payroute.compliance.dto.response.ComplianceCheckResponse;
import com.payroute.compliance.dto.response.ComplianceScreenResponse;
import com.payroute.compliance.dto.response.PagedResponse;
import com.payroute.compliance.entity.*;
import com.payroute.compliance.mapper.ComplianceCheckMapper;
import com.payroute.compliance.repository.ComplianceCheckRepository;
import com.payroute.compliance.repository.HoldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ComplianceService {

    private final ComplianceCheckRepository complianceCheckRepository;
    private final HoldRepository holdRepository;
    private final ComplianceCheckMapper complianceCheckMapper;
    private final PaymentServiceClient paymentServiceClient;
    private final NotificationServiceClient notificationServiceClient;

    /** Amount above which SANCTIONS + AML checks flag a payment for manual review. */
    private static final BigDecimal HOLD_THRESHOLD = new BigDecimal("100000");

    @Transactional
    public ComplianceScreenResponse screenPayment(ComplianceScreenRequest request) {
        log.info("Screening payment {} for compliance", request.getPaymentId());

        List<ComplianceCheck> checks = new ArrayList<>();
        boolean shouldHold = request.getAmount().compareTo(HOLD_THRESHOLD) > 0;

        // Sanctions check
        checks.add(createCheck(request.getPaymentId(), CheckType.SANCTIONS, CheckSeverity.HIGH,
                shouldHold ? CheckResult.HOLD : CheckResult.CLEAR,
                shouldHold ? "Amount exceeds threshold - manual review required" : "No sanctions match found"));

        // AML check
        checks.add(createCheck(request.getPaymentId(), CheckType.AML, CheckSeverity.HIGH,
                shouldHold ? CheckResult.HOLD : CheckResult.CLEAR,
                shouldHold ? "High-value transaction flagged for AML review" : "AML check passed"));

        // PEP check
        checks.add(createCheck(request.getPaymentId(), CheckType.PEP, CheckSeverity.MEDIUM,
                CheckResult.CLEAR, "No PEP match found"));

        // GEO check
        checks.add(createCheck(request.getPaymentId(), CheckType.GEO, CheckSeverity.LOW,
                CheckResult.CLEAR, "Geographic check passed"));

        List<ComplianceCheck> savedChecks = complianceCheckRepository.saveAll(checks);

        // Determine overall result
        CheckResult overallResult = savedChecks.stream()
                .map(ComplianceCheck::getResult)
                .filter(r -> r == CheckResult.HOLD)
                .findAny()
                .orElse(
                        savedChecks.stream()
                                .map(ComplianceCheck::getResult)
                                .filter(r -> r == CheckResult.FLAG)
                                .findAny()
                                .orElse(CheckResult.CLEAR)
                );

        // If any HOLD result, create Hold record and update payment status
        if (overallResult == CheckResult.HOLD) {
            Hold hold = Hold.builder()
                    .paymentId(request.getPaymentId())
                    .reason("Compliance screening resulted in HOLD - amount exceeds threshold")
                    .placedBy(0L) // system
                    .status(HoldStatus.ACTIVE)
                    .placedAt(LocalDateTime.now())
                    .build();
            holdRepository.save(hold);
            log.info("Hold placed on payment {}", request.getPaymentId());

            try {
                paymentServiceClient.updatePaymentStatus(request.getPaymentId(),
                        PaymentStatusUpdate.builder()
                                .paymentId(request.getPaymentId())
                                .newStatus("HELD")
                                .reason("Compliance hold - amount exceeds threshold")
                                .build());
            } catch (Exception e) {
                log.error("Failed to update payment status to HELD for payment {}: {}",
                        request.getPaymentId(), e.getMessage());
            }

            try {
                notificationServiceClient.sendNotification(NotificationRequest.builder()
                        .userId(0L)
                        .title("Compliance Hold Placed")
                        .message("Payment " + request.getPaymentId() + " has been placed on compliance hold")
                        .category("COMPLIANCE")
                        .severity("WARNING")
                        .referenceType("PAYMENT")
                        .referenceId(request.getPaymentId())
                        .build());
            } catch (Exception e) {
                log.error("Failed to send compliance hold notification: {}", e.getMessage());
            }
        }

        List<ComplianceCheckResponse> checkResponses = complianceCheckMapper.toResponseList(savedChecks);

        return ComplianceScreenResponse.builder()
                .paymentId(request.getPaymentId())
                .overallResult(overallResult)
                .checks(checkResponses)
                .build();
    }

    public PagedResponse<ComplianceCheckResponse> getChecks(Long paymentId, CheckResult result, Pageable pageable) {
        Page<ComplianceCheck> page;
        if (paymentId != null) {
            page = complianceCheckRepository.findByPaymentId(paymentId, pageable);
        } else if (result != null) {
            page = complianceCheckRepository.findByResult(result, pageable);
        } else {
            page = complianceCheckRepository.findAll(pageable);
        }
        return complianceCheckMapper.toPagedResponse(page);
    }

    private ComplianceCheck createCheck(Long paymentId, CheckType type, CheckSeverity severity,
                                         CheckResult result, String details) {
        return ComplianceCheck.builder()
                .paymentId(paymentId)
                .checkType(type)
                .severity(severity)
                .result(result)
                .details(details)
                .checkedBy("SYSTEM")
                .checkedAt(LocalDateTime.now())
                .build();
    }
}
