package com.payroute.payment.controller;

import com.payroute.payment.dto.request.PaymentInitiationRequest;
import com.payroute.payment.dto.request.PaymentStatusUpdate;
import com.payroute.payment.dto.response.ApiResponse;
import com.payroute.payment.dto.response.PagedResponse;
import com.payroute.payment.dto.response.PaymentResponse;
import com.payroute.payment.dto.response.PaymentStatsResponse;
import com.payroute.payment.entity.InitiationChannel;
import com.payroute.payment.entity.PaymentOrder;
import com.payroute.payment.entity.PaymentStatus;
import com.payroute.payment.mapper.PaymentMapper;
import com.payroute.payment.repository.PaymentOrderRepository;
import com.payroute.payment.service.PaymentOrchestrator;
import com.payroute.payment.service.PaymentService;
import com.payroute.payment.service.PaymentStatsService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "Payment orchestration and management APIs")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentOrchestrator paymentOrchestrator;
    private final PaymentMapper paymentMapper;
    private final PaymentStatsService paymentStatsService;
    private final PaymentOrderRepository paymentOrderRepository;

    @GetMapping("/stats")
    @Operation(summary = "Payment stats", description = "Aggregate payment metrics for dashboard (KPIs + time-series)")
    public ResponseEntity<ApiResponse<PaymentStatsResponse>> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        PaymentStatsResponse stats = paymentStatsService.getStats(from, to);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/aggregate")
    @Operation(summary = "Aggregate payments by IDs",
            description = "Returns count, totalAmount, and currency for the given payment IDs. Used by settlement-service.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> aggregateByIds(
            @RequestParam("ids") List<Long> ids) {
        Map<String, Object> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            out.put("count", 0L);
            out.put("totalAmount", BigDecimal.ZERO);
            out.put("currency", "INR");
            return ResponseEntity.ok(ApiResponse.success(out));
        }
        List<Object[]> rows = paymentOrderRepository.aggregateByIds(ids);
        long count = 0L;
        BigDecimal total = BigDecimal.ZERO;
        String currency = "INR";
        if (rows != null && !rows.isEmpty() && rows.get(0) != null) {
            Object[] r = rows.get(0);
            count = r[0] == null ? 0L : ((Number) r[0]).longValue();
            total = r[1] == null ? BigDecimal.ZERO : new BigDecimal(r[1].toString());
            if (r.length > 2 && r[2] != null) currency = r[2].toString();
        }
        out.put("count", count);
        out.put("totalAmount", total);
        out.put("currency", currency);
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    @PostMapping
    @Operation(summary = "Initiate a new payment", description = "Creates a new payment order and triggers orchestration")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @Valid @RequestBody PaymentInitiationRequest request,
            HttpServletRequest httpRequest) {

        String initiatedBy = httpRequest.getHeader("X-Username");
        if (initiatedBy == null || initiatedBy.isBlank()) {
            initiatedBy = "SYSTEM";
        }
        String role = httpRequest.getHeader("X-User-Role");
        String partyIdHeader = httpRequest.getHeader("X-Party-Id");
        Long callerPartyId = null;
        if (partyIdHeader != null && !partyIdHeader.isBlank()) {
            try { callerPartyId = Long.parseLong(partyIdHeader); } catch (NumberFormatException ignored) {}
        }
        String userIdHeader = httpRequest.getHeader("X-User-Id");
        Long callerUserId = null;
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try { callerUserId = Long.parseLong(userIdHeader); } catch (NumberFormatException ignored) {}
        }

        log.info("Payment initiation request received from user={} role={} partyId={}",
                initiatedBy, role, callerPartyId);

        PaymentOrder payment = paymentService.createPayment(
                request, initiatedBy, role, callerPartyId, callerUserId);
        PaymentResponse response = paymentMapper.toResponse(payment);

        // Trigger async orchestration
        paymentOrchestrator.orchestratePayment(payment);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment initiated successfully", response));
    }

    @GetMapping
    @Operation(summary = "List payments", description = "Paginated list of payments with optional filters")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentResponse>>> listPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) InitiationChannel channel,
            @RequestParam(required = false) String initiatedBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            HttpServletRequest httpRequest) {

        String userRole = httpRequest.getHeader("X-User-Role");
        String currentUser = httpRequest.getHeader("X-Username");

        // Role-based filtering: non-admin users can only see their own payments
        if (!"ADMIN".equalsIgnoreCase(userRole) && !"MANAGER".equalsIgnoreCase(userRole)) {
            initiatedBy = currentUser;
        }

        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PagedResponse<PaymentResponse> pagedResponse =
                paymentService.listPayments(status, channel, initiatedBy, pageable);

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment details", description = "Returns payment details including validation results")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable Long id) {
        PaymentResponse response = paymentService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an in-flight payment (Ops action)",
            description = "Forces the payment to FAILED with the provided reason. " +
                    "Forbidden for COMPLETED / FAILED / REVERSED. OPS/ADMIN only at gateway.")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancelPayment(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String reason = body.getOrDefault("reason", "Manual cancellation by Ops");
        String triggeredBy = httpRequest.getHeader("X-Username");
        if (triggeredBy == null || triggeredBy.isBlank()) triggeredBy = "OPS";
        PaymentOrder cancelled = paymentService.cancelPayment(id, reason, triggeredBy);
        // Auto-raise an exception so the audit trail records the manual cancel
        try {
            paymentOrchestrator.raiseManualException(cancelled, "SYSTEM", "MEDIUM",
                    "Manually cancelled by " + triggeredBy + ": " + reason);
        } catch (Exception ignore) { /* best-effort */ }
        return ResponseEntity.ok(ApiResponse.success("Payment cancelled", paymentMapper.toResponse(cancelled)));
    }

    @PostMapping("/{id}/hold")
    @Operation(summary = "Place an in-flight payment on hold (Ops action)",
            description = "Forces the payment to HELD with the provided reason. Can be released later " +
                    "via the existing compliance resume-after-hold flow. OPS/ADMIN only.")
    public ResponseEntity<ApiResponse<PaymentResponse>> holdPayment(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String reason = body.getOrDefault("reason", "Manual hold by Ops");
        String triggeredBy = httpRequest.getHeader("X-Username");
        if (triggeredBy == null || triggeredBy.isBlank()) triggeredBy = "OPS";
        PaymentOrder held = paymentService.holdPayment(id, reason, triggeredBy);
        try {
            paymentOrchestrator.raiseManualException(held, "SYSTEM", "MEDIUM",
                    "Manually held by " + triggeredBy + ": " + reason);
        } catch (Exception ignore) { /* best-effort */ }
        return ResponseEntity.ok(ApiResponse.success("Payment held", paymentMapper.toResponse(held)));
    }

    @PostMapping("/{id}/retry")
    @Operation(
            summary = "Retry a failed payment",
            description = "Reset a FAILED or VALIDATION_FAILED payment back to INITIATED and re-fire orchestration. " +
                    "Also auto-closes any linked exception cases. Restricted to OPS/ADMIN at the gateway layer.")
    public ResponseEntity<ApiResponse<PaymentResponse>> retryPayment(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {

        String triggeredBy = httpRequest.getHeader("X-Username");
        if (triggeredBy == null || triggeredBy.isBlank()) triggeredBy = "OPS";

        log.info("Retry requested for payment id={} by {}", id, triggeredBy);

        // Reset status (validates that the payment is in a retry-eligible state)
        PaymentOrder reset = paymentService.retryPayment(id, triggeredBy);

        // Best-effort: auto-close linked exception cases so the queue is clean
        try {
            paymentOrchestrator.autoCloseLinkedExceptions(id,
                    "Auto-resolved — payment retry triggered by " + triggeredBy);
        } catch (Exception ex) {
            log.warn("Failed to auto-close exception cases for retried payment id={}: {}", id, ex.getMessage());
        }

        // Re-fire orchestration asynchronously (same path as initial create)
        paymentOrchestrator.orchestratePayment(reset);

        PaymentResponse response = paymentMapper.toResponse(reset);
        return ResponseEntity.ok(ApiResponse.success("Payment retry scheduled", response));
    }

    @PostMapping("/{id}/resume-after-hold")
    @Operation(
            summary = "Resume orchestration after compliance hold release",
            description = "Internal callback used by compliance-service when an analyst releases a hold. " +
                    "Skips validation + compliance (already passed) and resumes at routing → processing.")
    public ResponseEntity<ApiResponse<PaymentResponse>> resumeAfterHold(@PathVariable Long id) {
        PaymentOrder payment = paymentService.getEntityById(id);
        // Mark VALIDATED first so the resume callback has a clean starting state
        paymentService.updateStatus(id, PaymentStatus.VALIDATED, "SYSTEM");
        paymentOrchestrator.resumeAfterHold(payment);
        PaymentResponse response = paymentMapper.toResponse(paymentService.getEntityById(id));
        return ResponseEntity.ok(ApiResponse.success("Payment resume scheduled", response));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update payment status", description = "Internal callback endpoint for status updates from routing-service")
    public ResponseEntity<ApiResponse<PaymentResponse>> updatePaymentStatus(
            @PathVariable Long id,
            @Valid @RequestBody PaymentStatusUpdate statusUpdate,
            HttpServletRequest httpRequest) {

        String updatedBy = httpRequest.getHeader("X-Username");
        if (updatedBy == null || updatedBy.isBlank()) {
            updatedBy = "SYSTEM";
        }

        log.info("Status update for payment id={} to {} by {}", id, statusUpdate.getNewStatus(), updatedBy);

        PaymentOrder updated = paymentService.updateStatus(id, statusUpdate.getNewStatus(), updatedBy);

        // Trigger post-status-change actions
        if (statusUpdate.getNewStatus() == PaymentStatus.COMPLETED) {
            paymentOrchestrator.onPaymentCompleted(id, statusUpdate.getRail());
        } else if (statusUpdate.getNewStatus() == PaymentStatus.FAILED) {
            paymentOrchestrator.onPaymentFailed(id, statusUpdate.getReason());
        }

        PaymentResponse response = paymentMapper.toResponse(updated);
        return ResponseEntity.ok(ApiResponse.success("Payment status updated", response));
    }
}
