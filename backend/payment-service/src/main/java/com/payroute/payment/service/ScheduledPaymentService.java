package com.payroute.payment.service;

import com.payroute.payment.client.NotificationServiceClient;
import com.payroute.payment.client.PartyServiceClient;
import com.payroute.payment.dto.client.AccountValidationResponse;
import com.payroute.payment.dto.client.NotificationRequest;
import com.payroute.payment.dto.request.PaymentInitiationRequest;
import com.payroute.payment.dto.request.ScheduledPaymentRequest;
import com.payroute.payment.dto.response.ApiResponse;
import com.payroute.payment.dto.response.ScheduledPaymentResponse;
import com.payroute.payment.entity.*;
import com.payroute.payment.exception.ForbiddenException;
import com.payroute.payment.exception.ResourceNotFoundException;
import com.payroute.payment.repository.ScheduledPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledPaymentService {

    private final ScheduledPaymentRepository repository;
    private final PaymentService paymentService;
    private final PaymentOrchestrator paymentOrchestrator;
    private final PartyServiceClient partyServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final com.payroute.payment.client.IamServiceClient iamServiceClient;

    private void sendScheduleNotification(ScheduledPayment s, String title, String message, String severity) {
        String userId = s.getCreatedBy();
        if (userId == null || userId.isBlank()) return;
        try {
            notificationServiceClient.sendNotification(NotificationRequest.builder()
                    .userId(userId)
                    .title(title)
                    .message(message)
                    .category("SCHEDULE")
                    .severity(severity)
                    .referenceType("SCHEDULED_PAYMENT")
                    .referenceId(s.getId())
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to send schedule notification for id={}: {}", s.getId(), ex.getMessage());
        }
    }

    // ---------- CRUD ----------

    @Transactional
    public ScheduledPaymentResponse create(ScheduledPaymentRequest req, Long userId, String createdBy) {
        return create(req, userId, createdBy, null, null);
    }

    @Transactional
    public ScheduledPaymentResponse create(ScheduledPaymentRequest req, Long userId, String createdBy,
                                           String callerRole, Long callerPartyId) {
        validateRequest(req);

        // §4.1 Transaction-PIN gate at schedule creation: customer must authorize the
        // schedule with their PIN. The schedule then fires automatically (no re-prompt).
        if ("CUSTOMER".equalsIgnoreCase(callerRole)) {
            if (userId == null) {
                throw new ForbiddenException("User context missing from request");
            }
            String pin = req.getTransactionPin();
            if (pin == null || pin.isBlank()) {
                throw new ForbiddenException("Transaction PIN is required to authorize this schedule");
            }
            try {
                ApiResponse<java.util.Map<String, Boolean>> resp = iamServiceClient
                        .verifyTransactionPin(userId, java.util.Map.of("pin", pin));
                Boolean valid = resp == null || resp.getData() == null ? null : resp.getData().get("valid");
                if (valid == null || !valid) {
                    throw new ForbiddenException("Incorrect transaction PIN");
                }
            } catch (ForbiddenException fe) {
                throw fe;
            } catch (Exception e) {
                log.warn("PIN verification call to iam-service failed for userId={}: {}",
                        userId, e.getMessage());
                throw new ForbiddenException("Unable to verify transaction PIN. Please try again.");
            }
        }

        // §4.2 Ownership enforcement at schedule-creation time.
        // The fire-time path runs as SCHEDULER (no party context), so this is the only
        // moment a customer could smuggle in another party's debtor account.
        if ("CUSTOMER".equalsIgnoreCase(callerRole)) {
            if (callerPartyId == null) {
                throw new ForbiddenException("Customer party context missing from request");
            }
            try {
                ApiResponse<AccountValidationResponse> resp =
                        partyServiceClient.validateAccountById(req.getDebtorAccountId());
                AccountValidationResponse debtor = resp == null ? null : resp.getData();
                if (debtor == null || !debtor.isExists()) {
                    throw new ForbiddenException("Debtor account not found");
                }
                if (debtor.getPartyId() == null || !callerPartyId.equals(debtor.getPartyId())) {
                    throw new ForbiddenException("You are not authorized to schedule debits from this account");
                }
            } catch (ForbiddenException fe) {
                throw fe;
            } catch (Exception e) {
                log.warn("Unable to verify debtor account ownership for scheduled payment: {}", e.getMessage());
                throw new ForbiddenException("Unable to verify account ownership");
            }
        }
        ScheduledPayment s = ScheduledPayment.builder()
                .userId(userId)
                .name(req.getName())
                .debtorAccountId(req.getDebtorAccountId())
                .creditorAccountId(req.getCreditorAccountId())
                .amount(req.getAmount())
                .currency(req.getCurrency().toUpperCase())
                .purposeCode(req.getPurposeCode())
                .remittanceInfo(req.getRemittanceInfo())
                .scheduleType(req.getScheduleType())
                .startAt(req.getStartAt())
                .endAt(req.getEndAt())
                .maxRuns(req.getMaxRuns())
                .runsCount(0)
                .nextRunAt(req.getStartAt())
                .status(ScheduledPaymentStatus.ACTIVE)
                .createdBy(createdBy)
                .build();
        s = repository.save(s);
        log.info("Created scheduled payment id={} type={} nextRunAt={}", s.getId(), s.getScheduleType(), s.getNextRunAt());
        sendScheduleNotification(s, "Scheduled payment created",
                "Your " + s.getScheduleType() + " schedule '" + s.getName() + "' for "
                        + s.getAmount() + " " + s.getCurrency() + " has been created. First run: "
                        + s.getNextRunAt(),
                "INFO");
        return toResponse(s);
    }

    @Transactional
    public ScheduledPaymentResponse update(Long id, ScheduledPaymentRequest req) {
        ScheduledPayment s = get(id);
        if (s.getStatus() == ScheduledPaymentStatus.COMPLETED
                || s.getStatus() == ScheduledPaymentStatus.CANCELLED) {
            throw new IllegalStateException("Cannot edit a " + s.getStatus() + " schedule");
        }
        validateRequest(req);
        s.setName(req.getName());
        s.setDebtorAccountId(req.getDebtorAccountId());
        s.setCreditorAccountId(req.getCreditorAccountId());
        s.setAmount(req.getAmount());
        s.setCurrency(req.getCurrency().toUpperCase());
        s.setPurposeCode(req.getPurposeCode());
        s.setRemittanceInfo(req.getRemittanceInfo());
        s.setScheduleType(req.getScheduleType());
        s.setStartAt(req.getStartAt());
        s.setEndAt(req.getEndAt());
        s.setMaxRuns(req.getMaxRuns());
        // If the startAt was moved and we haven't run yet, reset nextRunAt
        if (s.getRunsCount() == 0) {
            s.setNextRunAt(req.getStartAt());
        }
        return toResponse(repository.save(s));
    }

    @Transactional(readOnly = true)
    public Page<ScheduledPaymentResponse> list(Pageable pageable, Long userId) {
        Page<ScheduledPayment> page = (userId == null)
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ScheduledPaymentResponse getOne(Long id) {
        return toResponse(get(id));
    }

    @Transactional
    public ScheduledPaymentResponse pause(Long id) {
        ScheduledPayment s = get(id);
        if (s.getStatus() != ScheduledPaymentStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE schedules can be paused");
        }
        s.setStatus(ScheduledPaymentStatus.PAUSED);
        s = repository.save(s);
        sendScheduleNotification(s, "Scheduled payment paused",
                "Your schedule '" + s.getName() + "' has been paused.", "WARN");
        return toResponse(s);
    }

    @Transactional
    public ScheduledPaymentResponse resume(Long id) {
        ScheduledPayment s = get(id);
        if (s.getStatus() != ScheduledPaymentStatus.PAUSED) {
            throw new IllegalStateException("Only PAUSED schedules can be resumed");
        }
        s.setStatus(ScheduledPaymentStatus.ACTIVE);
        // If nextRunAt is in the past, bump to now so it fires on the next tick
        if (s.getNextRunAt() == null || s.getNextRunAt().isBefore(LocalDateTime.now())) {
            s.setNextRunAt(LocalDateTime.now());
        }
        s = repository.save(s);
        sendScheduleNotification(s, "Scheduled payment resumed",
                "Your schedule '" + s.getName() + "' is active again. Next run: " + s.getNextRunAt(),
                "INFO");
        return toResponse(s);
    }

    @Transactional
    public ScheduledPaymentResponse cancel(Long id) {
        ScheduledPayment s = get(id);
        if (s.getStatus() == ScheduledPaymentStatus.COMPLETED
                || s.getStatus() == ScheduledPaymentStatus.CANCELLED) {
            return toResponse(s);
        }
        s.setStatus(ScheduledPaymentStatus.CANCELLED);
        s.setNextRunAt(null);
        s = repository.save(s);
        sendScheduleNotification(s, "Scheduled payment cancelled",
                "Your schedule '" + s.getName() + "' has been cancelled.", "WARN");
        return toResponse(s);
    }

    // ---------- Scheduler dispatch ----------

    /**
     * Fires one due schedule. Creates a payment, orchestrates it, advances nextRunAt or completes.
     * Called by ScheduledPaymentWorker in its own transaction per schedule.
     */
    @Transactional
    public void fireOne(Long scheduleId) {
        ScheduledPayment s = repository.findById(scheduleId).orElse(null);
        if (s == null || s.getStatus() != ScheduledPaymentStatus.ACTIVE) return;
        if (s.getNextRunAt() == null || s.getNextRunAt().isAfter(LocalDateTime.now())) return;

        try {
            PaymentInitiationRequest req = PaymentInitiationRequest.builder()
                    .debtorAccountId(s.getDebtorAccountId())
                    .creditorAccountId(s.getCreditorAccountId())
                    .amount(s.getAmount())
                    .currency(s.getCurrency())
                    .purposeCode(s.getPurposeCode())
                    .initiationChannel(InitiationChannel.API)
                    .idempotencyKey("SCHED-" + s.getId() + "-RUN-" + (s.getRunsCount() + 1) + "-" + UUID.randomUUID())
                    .build();

            PaymentOrder payment = paymentService.createPayment(req, s.getCreatedBy() != null ? s.getCreatedBy() : "SCHEDULER");
            s.setLastPaymentId(payment.getId());
            s.setLastError(null);
            // Orchestrate asynchronously (same pattern as the payment API)
            paymentOrchestrator.orchestratePayment(payment);
            log.info("Scheduled payment id={} fired run #{} as payment id={}",
                    s.getId(), s.getRunsCount() + 1, payment.getId());
        } catch (Exception ex) {
            log.error("Scheduled payment id={} failed to fire: {}", s.getId(), ex.getMessage());
            s.setLastError(ex.getMessage() != null && ex.getMessage().length() > 490
                    ? ex.getMessage().substring(0, 490) : ex.getMessage());
            // Keep the schedule ACTIVE — we'll just advance nextRunAt and try again next cycle.
            // For ONCE schedules where the first fire fails, mark FAILED so it doesn't loop forever.
            sendScheduleNotification(s, "Scheduled payment failed to fire",
                    "Your schedule '" + s.getName() + "' could not execute: " + s.getLastError(),
                    "ERROR");
            if (s.getScheduleType() == ScheduleType.ONCE) {
                s.setStatus(ScheduledPaymentStatus.FAILED);
                s.setNextRunAt(null);
                repository.save(s);
                return;
            }
        }

        s.setRunsCount(s.getRunsCount() + 1);
        s.setLastRunAt(LocalDateTime.now());
        advanceNextRunAt(s);
        repository.save(s);
    }

    // ---------- helpers ----------

    private void advanceNextRunAt(ScheduledPayment s) {
        if (s.getScheduleType() == ScheduleType.ONCE) {
            s.setStatus(ScheduledPaymentStatus.COMPLETED);
            s.setNextRunAt(null);
            return;
        }
        if (s.getMaxRuns() != null && s.getRunsCount() >= s.getMaxRuns()) {
            s.setStatus(ScheduledPaymentStatus.COMPLETED);
            s.setNextRunAt(null);
            return;
        }
        LocalDateTime base = s.getNextRunAt() != null ? s.getNextRunAt() : LocalDateTime.now();
        LocalDateTime next = switch (s.getScheduleType()) {
            case DAILY -> base.plusDays(1);
            case WEEKLY -> base.plusWeeks(1);
            case MONTHLY -> base.plusMonths(1);
            default -> null;
        };
        // Skip any runs that have already passed (e.g., if the service was offline for a while)
        while (next != null && next.isBefore(LocalDateTime.now().minusSeconds(1))) {
            next = switch (s.getScheduleType()) {
                case DAILY -> next.plusDays(1);
                case WEEKLY -> next.plusWeeks(1);
                case MONTHLY -> next.plusMonths(1);
                default -> null;
            };
        }
        if (next != null && s.getEndAt() != null && next.isAfter(s.getEndAt())) {
            s.setStatus(ScheduledPaymentStatus.COMPLETED);
            s.setNextRunAt(null);
            return;
        }
        s.setNextRunAt(next);
    }

    private void validateRequest(ScheduledPaymentRequest req) {
        if (req.getDebtorAccountId() != null && req.getDebtorAccountId().equals(req.getCreditorAccountId())) {
            throw new IllegalArgumentException("Debtor and creditor accounts must differ");
        }
        if (req.getEndAt() != null && req.getStartAt() != null && req.getEndAt().isBefore(req.getStartAt())) {
            throw new IllegalArgumentException("endAt must be after startAt");
        }
    }

    private ScheduledPayment get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ScheduledPayment not found: " + id));
    }

    private ScheduledPaymentResponse toResponse(ScheduledPayment s) {
        return ScheduledPaymentResponse.builder()
                .id(s.getId()).userId(s.getUserId()).name(s.getName())
                .debtorAccountId(s.getDebtorAccountId()).creditorAccountId(s.getCreditorAccountId())
                .amount(s.getAmount()).currency(s.getCurrency()).purposeCode(s.getPurposeCode())
                .remittanceInfo(s.getRemittanceInfo()).scheduleType(s.getScheduleType())
                .startAt(s.getStartAt()).endAt(s.getEndAt()).maxRuns(s.getMaxRuns())
                .runsCount(s.getRunsCount()).nextRunAt(s.getNextRunAt()).lastRunAt(s.getLastRunAt())
                .lastPaymentId(s.getLastPaymentId()).lastError(s.getLastError())
                .status(s.getStatus()).createdBy(s.getCreatedBy())
                .createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt())
                .build();
    }
}
