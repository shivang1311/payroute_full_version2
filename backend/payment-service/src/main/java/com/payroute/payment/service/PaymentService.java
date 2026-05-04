package com.payroute.payment.service;

import com.payroute.payment.dto.request.PaymentInitiationRequest;
import com.payroute.payment.dto.response.PagedResponse;
import com.payroute.payment.dto.response.PaymentResponse;
import com.payroute.payment.dto.response.ValidationResultResponse;
import com.payroute.payment.entity.PaymentMethod;
import com.payroute.payment.entity.PaymentOrder;
import com.payroute.payment.entity.PaymentStatus;
import com.payroute.payment.entity.ValidationResult;
import com.payroute.payment.exception.DuplicatePaymentException;
import com.payroute.payment.exception.PaymentValidationException;
import com.payroute.payment.exception.ResourceNotFoundException;
import com.payroute.payment.mapper.PaymentMapper;
import com.payroute.payment.mapper.ValidationResultMapper;
import com.payroute.payment.client.IamServiceClient;
import com.payroute.payment.client.PartyServiceClient;
import com.payroute.payment.dto.client.AccountValidationResponse;
import com.payroute.payment.dto.response.ApiResponse;
import com.payroute.payment.exception.ForbiddenException;
import com.payroute.payment.repository.PaymentOrderRepository;
import com.payroute.payment.repository.ValidationResultRepository;
import com.payroute.payment.util.PaymentReferenceGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    /** UPI: max ₹1,00,000 per single transaction. */
    private static final BigDecimal UPI_TXN_LIMIT = new BigDecimal("100000");

    /** UPI: max ₹1,00,000 cumulative per debtor account per calendar day. */
    private static final BigDecimal UPI_DAILY_LIMIT = new BigDecimal("100000");

    /** Bank transfer: max ₹5,00,000 in a single transaction. */
    private static final BigDecimal BANK_TRANSFER_PER_TXN_LIMIT = new BigDecimal("500000");

    private final PaymentOrderRepository paymentOrderRepository;
    private final ValidationResultRepository validationResultRepository;
    private final PaymentMapper paymentMapper;
    private final ValidationResultMapper validationResultMapper;
    private final PartyServiceClient partyServiceClient;
    private final IamServiceClient iamServiceClient;

    /**
     * Backward-compatible entry point for internal callers (scheduler, bulk upload, internal retries)
     * that don't propagate a role/partyId. Skips ownership enforcement and PIN verification.
     */
    @Transactional
    public PaymentOrder createPayment(PaymentInitiationRequest request, String initiatedBy) {
        return createPayment(request, initiatedBy, null, null, null);
    }

    /** Backward-compatible 4-arg entry point — used by paths that don't have userId. */
    @Transactional
    public PaymentOrder createPayment(PaymentInitiationRequest request, String initiatedBy,
                                      String callerRole, Long callerPartyId) {
        return createPayment(request, initiatedBy, callerRole, callerPartyId, null);
    }

    @Transactional
    public PaymentOrder createPayment(PaymentInitiationRequest request, String initiatedBy,
                                      String callerRole, Long callerPartyId, Long callerUserId) {
        log.info("Creating payment for debtor={} creditor={} amount={} currency={}",
                request.getDebtorAccountId(), request.getCreditorAccountId(),
                request.getAmount(), request.getCurrency());

        // Fail fast on self-payments — debtor and creditor accounts must differ
        if (request.getDebtorAccountId() != null
                && request.getDebtorAccountId().equals(request.getCreditorAccountId())) {
            throw new IllegalArgumentException("Debtor and creditor accounts must differ");
        }

        // §4.1 Transaction-PIN gate: a CUSTOMER must supply their PIN with every payment.
        // The PIN is verified against iam-service before any DB write.
        if ("CUSTOMER".equalsIgnoreCase(callerRole)) {
            if (callerUserId == null) {
                throw new ForbiddenException("User context missing from request");
            }
            String pin = request.getTransactionPin();
            if (pin == null || pin.isBlank()) {
                throw new ForbiddenException("Transaction PIN is required to authorize this payment");
            }
            try {
                ApiResponse<java.util.Map<String, Boolean>> resp = iamServiceClient
                        .verifyTransactionPin(callerUserId, java.util.Map.of("pin", pin));
                Boolean valid = resp == null || resp.getData() == null ? null : resp.getData().get("valid");
                if (valid == null || !valid) {
                    throw new ForbiddenException("Incorrect transaction PIN");
                }
            } catch (ForbiddenException fe) {
                throw fe;
            } catch (Exception e) {
                log.warn("PIN verification call to iam-service failed for userId={}: {}",
                        callerUserId, e.getMessage());
                throw new ForbiddenException("Unable to verify transaction PIN. Please try again.");
            }
        }

        // §4.2 Ownership enforcement: a CUSTOMER may only debit an account they own.
        // ADMIN / OPERATIONS can debit any account on behalf of any customer.
        if ("CUSTOMER".equalsIgnoreCase(callerRole)) {
            if (callerPartyId == null) {
                throw new ForbiddenException("Customer party context missing from request");
            }
            Long debtorAccountId = request.getDebtorAccountId();
            if (debtorAccountId == null) {
                throw new ForbiddenException("Debtor account is required");
            }
            AccountValidationResponse debtor;
            try {
                ApiResponse<AccountValidationResponse> resp =
                        partyServiceClient.validateAccountById(debtorAccountId);
                debtor = resp == null ? null : resp.getData();
            } catch (Exception e) {
                log.warn("Unable to resolve debtor account {} for ownership check: {}",
                        debtorAccountId, e.getMessage());
                throw new ForbiddenException("Unable to verify account ownership");
            }
            if (debtor == null || !debtor.isExists()) {
                throw new ForbiddenException("Debtor account not found");
            }
            if (debtor.getPartyId() == null || !callerPartyId.equals(debtor.getPartyId())) {
                log.warn("Ownership violation: user partyId={} attempted to debit accountId={} " +
                                "owned by partyId={}", callerPartyId, debtorAccountId, debtor.getPartyId());
                throw new ForbiddenException(
                        "You are not authorized to debit this account");
            }
        }

        // Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<PaymentOrder> existing = paymentOrderRepository
                    .findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                throw new DuplicatePaymentException(
                        "Payment with idempotency key already exists",
                        existing.get().getId());
            }
        }

        // Synchronous limit pre-checks — give the customer immediate feedback at submit time
        // instead of letting the payment land in VALIDATION_FAILED a few seconds later.
        // (The async LimitChecker still runs as a backstop.)

        // 1) UPI: max ₹1,00,000 per transaction, AND ₹1,00,000 cumulative per account per day.
        if (request.getPaymentMethod() == PaymentMethod.UPI && request.getDebtorAccountId() != null) {
            // Per-transaction overflow: tell the user to switch to bank transfer.
            if (request.getAmount().compareTo(UPI_TXN_LIMIT) > 0) {
                throw new PaymentValidationException(
                        "Amount ₹" + request.getAmount() + " exceeds the UPI per-transaction limit of ₹"
                                + UPI_TXN_LIMIT + ". Please switch to Bank Transfer for larger payments "
                                + "(up to ₹" + BANK_TRANSFER_PER_TXN_LIMIT + " per transaction).");
            }

            LocalDate today = LocalDate.now();
            BigDecimal alreadyUsed = paymentOrderRepository.sumByDebtorAndMethodInWindow(
                    request.getDebtorAccountId(),
                    PaymentMethod.UPI,
                    today.atStartOfDay(),
                    today.atTime(LocalTime.MAX));
            if (alreadyUsed == null) alreadyUsed = BigDecimal.ZERO;
            BigDecimal projected = alreadyUsed.add(request.getAmount());
            if (projected.compareTo(UPI_DAILY_LIMIT) > 0) {
                BigDecimal remaining = UPI_DAILY_LIMIT.subtract(alreadyUsed).max(BigDecimal.ZERO);
                throw new PaymentValidationException(
                        "UPI daily limit exceeded. Max ₹" + UPI_DAILY_LIMIT
                                + " per account per day. Already used today: ₹" + alreadyUsed
                                + ". Remaining today: ₹" + remaining
                                + ". Please use Bank Transfer for larger amounts.");
            }
        }

        // 2) Bank transfer: per-transaction ceiling of ₹5,00,000.
        if (request.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                && request.getAmount().compareTo(BANK_TRANSFER_PER_TXN_LIMIT) > 0) {
            throw new PaymentValidationException(
                    "Amount ₹" + request.getAmount() + " exceeds the Bank Transfer per-transaction limit of ₹"
                            + BANK_TRANSFER_PER_TXN_LIMIT + ". Please split the payment into smaller transactions.");
        }

        PaymentOrder payment = paymentMapper.toEntity(request);
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setInitiatedBy(initiatedBy);
        payment.setCreatedBy(initiatedBy);
        payment.setUpdatedBy(initiatedBy);

        PaymentOrder saved = paymentOrderRepository.save(payment);

        // Derive customer-facing reference from the now-known id + createdAt,
        // then persist it so future reads (and lookups by reference) are stable.
        if (saved.getReference() == null) {
            saved.setReference(PaymentReferenceGenerator.generate(saved.getId(), saved.getCreatedAt()));
            saved = paymentOrderRepository.save(saved);
        }
        log.info("Payment created with id={} reference={}", saved.getId(), saved.getReference());
        return saved;
    }

    @Transactional(readOnly = true)
    public PaymentResponse getById(Long id) {
        PaymentOrder payment = paymentOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentOrder", id));

        PaymentResponse response = paymentMapper.toResponse(payment);

        List<ValidationResult> validations = validationResultRepository.findByPaymentId(id);
        List<ValidationResultResponse> validationResponses = validationResultMapper.toResponseList(validations);
        response.setValidationResults(validationResponses);

        return response;
    }

    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponse> listPayments(PaymentStatus status, String initiatedBy,
                                                        Pageable pageable) {
        Page<PaymentOrder> page;

        if (status != null) {
            page = paymentOrderRepository.findByStatus(status, pageable);
        } else if (initiatedBy != null && !initiatedBy.isBlank()) {
            page = paymentOrderRepository.findByInitiatedBy(initiatedBy, pageable);
        } else {
            page = paymentOrderRepository.findAll(pageable);
        }

        List<PaymentResponse> responses = paymentMapper.toResponseList(page.getContent());

        return PagedResponse.of(
                responses,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    @Transactional
    public PaymentOrder updateStatus(Long paymentId, PaymentStatus newStatus, String updatedBy) {
        PaymentOrder payment = paymentOrderRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentOrder", paymentId));

        log.info("Updating payment id={} status from {} to {}", paymentId, payment.getStatus(), newStatus);

        payment.setStatus(newStatus);
        payment.setUpdatedBy(updatedBy);

        return paymentOrderRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public PaymentOrder getEntityById(Long id) {
        return paymentOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentOrder", id));
    }

    /**
     * Manually cancel an in-flight payment (Ops action).
     * Forbidden for terminal states (COMPLETED / FAILED / REVERSED) — those
     * already settled or already failed and need a separate reversal flow.
     */
    @Transactional
    public PaymentOrder cancelPayment(Long paymentId, String reason, String updatedBy) {
        PaymentOrder payment = paymentOrderRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentOrder", paymentId));

        PaymentStatus s = payment.getStatus();
        if (s == PaymentStatus.COMPLETED || s == PaymentStatus.REVERSED || s == PaymentStatus.FAILED) {
            throw new IllegalStateException(
                    "Payment id=" + paymentId + " cannot be cancelled — current status is " + s);
        }
        log.info("Manually cancelling payment id={} (was {}). Reason: {}", paymentId, s, reason);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setUpdatedBy(updatedBy);
        return paymentOrderRepository.save(payment);
    }

    /**
     * Manually place an in-flight payment on HOLD so an analyst can investigate.
     * The existing compliance "resume-after-hold" flow can later release it.
     * Only allowed from non-terminal, non-already-held states.
     */
    @Transactional
    public PaymentOrder holdPayment(Long paymentId, String reason, String updatedBy) {
        PaymentOrder payment = paymentOrderRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentOrder", paymentId));

        PaymentStatus s = payment.getStatus();
        if (s == PaymentStatus.COMPLETED || s == PaymentStatus.REVERSED
                || s == PaymentStatus.FAILED || s == PaymentStatus.HELD) {
            throw new IllegalStateException(
                    "Payment id=" + paymentId + " cannot be held — current status is " + s);
        }
        log.info("Manually holding payment id={} (was {}). Reason: {}", paymentId, s, reason);
        payment.setStatus(PaymentStatus.HELD);
        payment.setUpdatedBy(updatedBy);
        return paymentOrderRepository.save(payment);
    }

    /**
     * Reset a previously failed payment back to INITIATED so the orchestrator can
     * re-process it. Only payments in a terminal failure state (FAILED or
     * VALIDATION_FAILED) are eligible — anything else is a no-op or rejected.
     */
    @Transactional
    public PaymentOrder retryPayment(Long paymentId, String updatedBy) {
        PaymentOrder payment = paymentOrderRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentOrder", paymentId));

        PaymentStatus currentStatus = payment.getStatus();
        if (currentStatus != PaymentStatus.FAILED
                && currentStatus != PaymentStatus.VALIDATION_FAILED) {
            throw new IllegalStateException(
                    "Payment id=" + paymentId + " cannot be retried — status is "
                            + currentStatus + ". Retry is only allowed for FAILED or VALIDATION_FAILED payments.");
        }

        log.info("Retrying payment id={} (was {}); resetting to INITIATED", paymentId, currentStatus);
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setUpdatedBy(updatedBy);
        return paymentOrderRepository.save(payment);
    }
}
