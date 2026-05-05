package com.payroute.payment.service;

import com.payroute.payment.client.*;
import com.payroute.payment.dto.client.*;
import com.payroute.payment.entity.PaymentOrder;
import com.payroute.payment.entity.PaymentStatus;
import com.payroute.payment.exception.PaymentValidationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOrchestrator {

    private final PaymentService paymentService;
    private final PaymentValidationService paymentValidationService;
    private final PartyServiceClient partyServiceClient;
    private final ComplianceServiceClient complianceServiceClient;
    private final RoutingServiceClient routingServiceClient;
    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final ExceptionServiceClient exceptionServiceClient;

    /**
     * Main orchestration flow for a payment.
     * Runs asynchronously after payment creation.
     *
     * Steps:
     * 1. Validate accounts via PartyServiceClient
     * 2. Run internal PaymentValidationService
     * 3. If validated, call ComplianceServiceClient.screen
     * 4. If compliance CLEAR, call RoutingServiceClient.routePayment
     * 5. Status updates trigger notifications (best-effort)
     */
    @Async("orchestrationExecutor")
    public void orchestratePayment(PaymentOrder payment) {
        log.info("Starting orchestration for payment id={}", payment.getId());

        try {
            // Step 1: Validate debtor and creditor accounts via party-service
            validateAccounts(payment);

            // Step 2: Run internal validations
            boolean validationPassed = paymentValidationService.validate(payment);
            if (!validationPassed) {
                paymentService.updateStatus(payment.getId(), PaymentStatus.VALIDATION_FAILED, "SYSTEM");
                sendNotificationSafe(payment, "Payment Validation Failed",
                        "Payment " + label(payment) + " failed validation checks");
                publishWebhookSafe(paymentService.getEntityById(payment.getId()), "PAYMENT_FAILED");
                raiseExceptionSafe(payment, "VALIDATION", "MEDIUM",
                        "Payment " + label(payment) + " failed internal validation checks");
                log.warn("Payment id={} failed validation", payment.getId());
                return;
            }

            paymentService.updateStatus(payment.getId(), PaymentStatus.VALIDATED, "SYSTEM");
            sendNotificationSafe(payment, "Payment Validated",
                    "Payment " + label(payment) + " has been validated");

            // Step 3: Compliance screening
            paymentService.updateStatus(payment.getId(), PaymentStatus.SCREENING, "SYSTEM");
            ComplianceScreenResponse complianceResult = screenPayment(payment);

            if ("HOLD".equals(complianceResult.getOverallResult())) {
                paymentService.updateStatus(payment.getId(), PaymentStatus.HELD, "SYSTEM");
                sendNotificationSafe(payment, "Payment Held",
                        "Payment " + label(payment) + " is held for compliance review");
                publishWebhookSafe(paymentService.getEntityById(payment.getId()), "PAYMENT_HELD");
                log.info("Payment id={} held for compliance review", payment.getId());
                return;
            }

            if ("FLAG".equals(complianceResult.getOverallResult())) {
                paymentService.updateStatus(payment.getId(), PaymentStatus.HELD, "SYSTEM");
                sendNotificationSafe(payment, "Payment Flagged",
                        "Payment " + label(payment) + " has been flagged by compliance");
                publishWebhookSafe(paymentService.getEntityById(payment.getId()), "PAYMENT_HELD");
                log.info("Payment id={} flagged by compliance", payment.getId());
                return;
            }

            // Step 4: Route payment
            // Guard: re-read current status before routing. If Ops manually HELD or
            // cancelled (FAILED) the payment while compliance was running, abort here
            // so we don't overwrite that manual action.
            PaymentStatus currentStatus = paymentService.getCurrentStatus(payment.getId());
            if (currentStatus == PaymentStatus.HELD
                    || currentStatus == PaymentStatus.CANCELLED
                    || currentStatus == PaymentStatus.FAILED) {
                log.info("Payment id={} is now {} (manual Ops action) — aborting background orchestration",
                        payment.getId(), currentStatus);
                return;
            }
            paymentService.updateStatus(payment.getId(), PaymentStatus.ROUTED, "SYSTEM");
            RouteRequest routeRequest = RouteRequest.builder()
                    .paymentId(payment.getId())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .debtorAccountId(payment.getDebtorAccountId())
                    .creditorAccountId(payment.getCreditorAccountId())
                    .purposeCode(payment.getPurposeCode())
                    .channel(payment.getInitiationChannel() != null
                            ? payment.getInitiationChannel().name() : null)
                    .build();

            RailInstructionResponse routeResponse = routePayment(routeRequest);
            log.info("Payment id={} routed via rail={}, correlationRef={}",
                    payment.getId(), routeResponse.getRail(), routeResponse.getCorrelationRef());

            paymentService.updateStatus(payment.getId(), PaymentStatus.PROCESSING, "SYSTEM");
            sendNotificationSafe(payment, "Payment Processing",
                    "Payment " + label(payment) + " is being processed via " + routeResponse.getRail());

        } catch (PaymentValidationException ex) {
            log.error("Payment id={} orchestration failed during validation: {}",
                    payment.getId(), ex.getMessage());
            paymentService.updateStatus(payment.getId(), PaymentStatus.VALIDATION_FAILED, "SYSTEM");
            sendNotificationSafe(payment, "Payment Failed",
                    "Payment " + label(payment) + " failed: " + ex.getMessage());
            publishWebhookSafe(paymentService.getEntityById(payment.getId()), "PAYMENT_FAILED");
            raiseExceptionSafe(payment, "VALIDATION", "HIGH",
                    "Payment " + label(payment) + " failed validation: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Payment id={} orchestration failed unexpectedly", payment.getId(), ex);
            paymentService.updateStatus(payment.getId(), PaymentStatus.FAILED, "SYSTEM");
            sendNotificationSafe(payment, "Payment Failed",
                    "Payment " + label(payment) + " failed due to an unexpected error");
            publishWebhookSafe(paymentService.getEntityById(payment.getId()), "PAYMENT_FAILED");
            raiseExceptionSafe(payment, "SYSTEM", "HIGH",
                    "Payment " + label(payment) + " failed unexpectedly: " + ex.getMessage());
        }
    }

    /**
     * Called when routing-service reports success.
     * Posts DEBIT + CREDIT + FEE ledger entries via ledger-service in one atomic call.
     */
    public void onPaymentCompleted(Long paymentId, String rail) {
        log.info("Payment id={} completed via rail={}, posting ledger entries", paymentId, rail);

        PaymentOrder payment = paymentService.getEntityById(paymentId);
        paymentService.updateStatus(paymentId, PaymentStatus.COMPLETED, "SYSTEM");

        String effectiveRail = rail;
        if (effectiveRail == null || effectiveRail.isBlank()) {
            effectiveRail = "BOOK"; // safe fallback so fee lookup still resolves
            log.warn("No rail provided for payment id={}, defaulting to {}", paymentId, effectiveRail);
        }

        try {
            unwrap(ledgerServiceClient.postPaymentEntries(
                    paymentId,
                    payment.getDebtorAccountId(),
                    payment.getCreditorAccountId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    effectiveRail,
                    payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : null));
            log.info("Ledger entries (debit+credit+fee) posted for payment id={}", paymentId);

        } catch (Exception ex) {
            log.error("Failed to post ledger entries for payment id={}", paymentId, ex);
        }

        sendNotificationSafe(payment, "Payment Completed",
                "Payment " + label(payment) + " has been completed successfully");
        publishWebhookSafe(paymentService.getEntityById(paymentId), "PAYMENT_COMPLETED");
    }

    /** Backward-compatible overload for callers that don't know the rail. */
    public void onPaymentCompleted(Long paymentId) {
        onPaymentCompleted(paymentId, null);
    }

    /**
     * Resume orchestration after a compliance hold is released. The payment has
     * already passed validation + compliance, so we skip those steps and pick up
     * at routing → processing → settlement.
     *
     * <p>Runs asynchronously the same way as {@link #orchestratePayment(PaymentOrder)}
     * so the API call returns immediately.
     */
    @Async("orchestrationExecutor")
    public void resumeAfterHold(PaymentOrder payment) {
        log.info("Resuming orchestration after hold release for payment id={}", payment.getId());
        try {
            // Treat the payment as compliance-cleared; jump straight to routing.
            paymentService.updateStatus(payment.getId(), PaymentStatus.ROUTED, "SYSTEM");

            RouteRequest routeRequest = RouteRequest.builder()
                    .paymentId(payment.getId())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .debtorAccountId(payment.getDebtorAccountId())
                    .creditorAccountId(payment.getCreditorAccountId())
                    .purposeCode(payment.getPurposeCode())
                    .channel(payment.getInitiationChannel() != null
                            ? payment.getInitiationChannel().name() : null)
                    .build();

            RailInstructionResponse routeResponse = routePayment(routeRequest);
            log.info("Resumed payment id={} routed via rail={}",
                    payment.getId(), routeResponse.getRail());

            paymentService.updateStatus(payment.getId(), PaymentStatus.PROCESSING, "SYSTEM");
            sendNotificationSafe(payment, "Payment Resumed",
                    "Payment " + label(payment) + " resumed after compliance release; processing via "
                            + routeResponse.getRail());
        } catch (Exception ex) {
            log.error("Resume-after-hold failed for payment id={}: {}", payment.getId(), ex.getMessage());
            paymentService.updateStatus(payment.getId(), PaymentStatus.FAILED, "SYSTEM");
            sendNotificationSafe(payment, "Payment Failed",
                    "Payment " + label(payment) + " failed to resume after hold release: " + ex.getMessage());
            publishWebhookSafe(paymentService.getEntityById(payment.getId()), "PAYMENT_FAILED");
            raiseExceptionSafe(payment, "RAIL", "HIGH",
                    "Payment " + label(payment) + " failed to resume after hold release: " + ex.getMessage());
        }
    }

    /**
     * Handles failure scenario for a payment.
     */
    public void onPaymentFailed(Long paymentId, String reason) {
        log.warn("Payment id={} failed. Reason: {}", paymentId, reason);

        PaymentOrder payment = paymentService.getEntityById(paymentId);
        paymentService.updateStatus(paymentId, PaymentStatus.FAILED, "SYSTEM");

        sendNotificationSafe(payment, "Payment Failed",
                "Payment " + label(payment) + " has failed. Reason: " + reason);
        publishWebhookSafe(paymentService.getEntityById(paymentId), "PAYMENT_FAILED");
        raiseExceptionSafe(payment, "RAIL", "HIGH",
                "Payment " + label(payment) + " failed at the rail: " + reason);
    }

    // --- Circuit-breaker-protected Feign calls ---

    @CircuitBreaker(name = "partyService", fallbackMethod = "validateAccountsFallback")
    private void validateAccounts(PaymentOrder payment) {
        log.info("Validating accounts for payment id={}", payment.getId());

        AccountValidationResponse debtorValidation = unwrap(partyServiceClient
                .validateAccountById(payment.getDebtorAccountId()));

        if (debtorValidation == null || !debtorValidation.isExists() || !debtorValidation.isActive()) {
            throw new PaymentValidationException(
                    "Debtor account " + payment.getDebtorAccountId() + " is invalid or inactive");
        }

        AccountValidationResponse creditorValidation = unwrap(partyServiceClient
                .validateAccountById(payment.getCreditorAccountId()));

        if (creditorValidation == null || !creditorValidation.isExists() || !creditorValidation.isActive()) {
            throw new PaymentValidationException(
                    "Creditor account " + payment.getCreditorAccountId() + " is invalid or inactive");
        }

        log.info("Account validation passed for payment id={}", payment.getId());
    }

    private static <T> T unwrap(com.payroute.payment.dto.response.ApiResponse<T> resp) {
        return resp == null ? null : resp.getData();
    }

    @SuppressWarnings("unused")
    private void validateAccountsFallback(PaymentOrder payment, Throwable t) {
        log.error("Party service unavailable for payment id={}. Error: {}", payment.getId(), t.getMessage());
        throw new PaymentValidationException(
                "Account validation service is temporarily unavailable. Please try again later.");
    }

    @CircuitBreaker(name = "complianceService", fallbackMethod = "screenPaymentFallback")
    private ComplianceScreenResponse screenPayment(PaymentOrder payment) {
        log.info("Screening payment id={} for compliance", payment.getId());

        ComplianceScreenRequest request = ComplianceScreenRequest.builder()
                .paymentId(payment.getId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .debtorAccountId(payment.getDebtorAccountId())
                .creditorAccountId(payment.getCreditorAccountId())
                .build();

        ComplianceScreenResponse resp = unwrap(complianceServiceClient.screen(request));
        if (resp == null) {
            throw new RuntimeException("Empty compliance response for payment " + payment.getId());
        }
        return resp;
    }

    @SuppressWarnings("unused")
    private ComplianceScreenResponse screenPaymentFallback(PaymentOrder payment, Throwable t) {
        log.error("Compliance service unavailable for payment id={}. Holding payment. Error: {}",
                payment.getId(), t.getMessage());
        // When compliance service is down, hold the payment for manual review
        return ComplianceScreenResponse.builder()
                .paymentId(payment.getId())
                .overallResult("HOLD")
                .build();
    }

    @CircuitBreaker(name = "routingService", fallbackMethod = "routePaymentFallback")
    private RailInstructionResponse routePayment(RouteRequest request) {
        RailInstructionResponse resp = unwrap(routingServiceClient.routePayment(request));
        if (resp == null) {
            throw new RuntimeException("Empty routing response for payment " + request.getPaymentId());
        }
        return resp;
    }

    @SuppressWarnings("unused")
    private RailInstructionResponse routePaymentFallback(RouteRequest request, Throwable t) {
        log.error("Routing service unavailable for payment id={}. Error: {}",
                request.getPaymentId(), t.getMessage());
        throw new RuntimeException("Routing service is temporarily unavailable. Payment will be retried.");
    }

    @CircuitBreaker(name = "ledgerService", fallbackMethod = "postLedgerEntryFallback")
    private LedgerEntryResponse postLedgerEntry(LedgerPostRequest request) {
        return unwrap(ledgerServiceClient.postEntry(request));
    }

    @SuppressWarnings("unused")
    private LedgerEntryResponse postLedgerEntryFallback(LedgerPostRequest request, Throwable t) {
        log.error("Ledger service unavailable for payment id={}. Error: {}",
                request.getPaymentId(), t.getMessage());
        throw new RuntimeException("Ledger service is temporarily unavailable.");
    }

    /**
     * Public wrapper around {@link #raiseExceptionSafe} so controllers can trigger
     * an audit-trail exception case for manual Ops actions (cancel, hold, etc.).
     */
    public void raiseManualException(PaymentOrder payment, String category, String priority, String description) {
        raiseExceptionSafe(payment, category, priority, description);
    }

    /**
     * Tell exception-service to mark all open cases for this payment as RESOLVED.
     * Used when the payment is retried — keeps the Exception Queue clean without
     * requiring the Ops officer to manually close every linked row.
     */
    public void autoCloseLinkedExceptions(Long paymentId, String reason) {
        exceptionServiceClient.autoCloseForPayment(paymentId, reason);
    }

    // --- Best-effort exception-case raise ---

    /**
     * Auto-raise an exception case for a failing payment. Best-effort: any error
     * here is logged but does NOT propagate, because we don't want exception-service
     * outages to mask the underlying payment failure that already happened.
     */
    private void raiseExceptionSafe(PaymentOrder payment, String category, String priority, String description) {
        try {
            // ownerId left null on auto-creation — an Ops officer claims/assigns it later
            ExceptionCaseRequest req = ExceptionCaseRequest.builder()
                    .paymentId(payment.getId())
                    .category(category)
                    .priority(priority)
                    .description(description)
                    .build();
            exceptionServiceClient.createException(req);
            log.info("Auto-raised {} exception (priority={}) for payment id={}",
                    category, priority, payment.getId());
        } catch (Exception ex) {
            log.warn("Failed to auto-raise exception case for payment id={}: {}",
                    payment.getId(), ex.getMessage());
        }
    }

    // --- Best-effort webhook fan-out ---

    private void publishWebhookSafe(PaymentOrder payment, String eventType) {
        try {
            java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("paymentId", payment.getId());
            data.put("amount", payment.getAmount());
            data.put("currency", payment.getCurrency());
            data.put("debtorAccountId", payment.getDebtorAccountId());
            data.put("creditorAccountId", payment.getCreditorAccountId());
            data.put("status", payment.getStatus() != null ? payment.getStatus().name() : null);
            data.put("purposeCode", payment.getPurposeCode());

            notificationServiceClient.publishWebhookEvent(
                    com.payroute.payment.dto.client.WebhookEventRequest.builder()
                            .eventType(eventType)
                            .referenceId(payment.getId())
                            .userId(null) // fan-out to all subscribers; can be scoped later
                            .data(data)
                            .build());
        } catch (Exception ex) {
            log.warn("Failed to publish webhook event {} for payment id={}: {}",
                    eventType, payment.getId(), ex.getMessage());
        }
    }

    /**
     * Customer-facing label for this payment — prefers the opaque reference (PR…),
     * falls back to "#id" if the reference hasn't been generated yet.
     */
    private static String label(PaymentOrder p) {
        return p.getReference() != null && !p.getReference().isBlank()
                ? p.getReference()
                : "#" + p.getId();
    }

    // --- Best-effort notification ---

    private void sendNotificationSafe(PaymentOrder payment, String title, String message) {
        try {
            NotificationRequest notificationRequest = NotificationRequest.builder()
                    .userId(payment.getInitiatedBy())
                    .title(title)
                    .message(message)
                    .category("PAYMENT")
                    .severity("INFO")
                    .referenceType("PAYMENT")
                    .referenceId(payment.getId())
                    .build();
            notificationServiceClient.sendNotification(notificationRequest);
        } catch (Exception ex) {
            log.warn("Failed to send notification for payment id={}: {}",
                    payment.getId(), ex.getMessage());
        }
    }
}
