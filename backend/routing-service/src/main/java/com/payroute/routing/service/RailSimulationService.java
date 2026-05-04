package com.payroute.routing.service;

import com.payroute.routing.client.PaymentServiceClient;
import com.payroute.routing.dto.request.PaymentStatusUpdate;
import com.payroute.routing.entity.RailInstruction;
import com.payroute.routing.entity.RailStatus;
import com.payroute.routing.repository.RailInstructionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class RailSimulationService {

    private final RailInstructionRepository railInstructionRepository;
    private final PaymentServiceClient paymentServiceClient;

    @Value("${payroute.simulation.delay-min-ms:2000}")
    private int delayMinMs;

    @Value("${payroute.simulation.delay-max-ms:5000}")
    private int delayMaxMs;

    @Value("${payroute.simulation.failure-rate:0.1}")
    private double failureRate;

    @Value("${payroute.simulation.max-retries:3}")
    private int maxRetries;

    private final Random random = new Random();

    @Async("railSimExecutor")
    public void simulateRail(RailInstruction instruction) {
        log.info("Starting rail simulation for instruction id={}, rail={}, correlationRef={}",
                instruction.getId(), instruction.getRail(), instruction.getCorrelationRef());

        processInstruction(instruction);
    }

    private void processInstruction(RailInstruction instruction) {
        try {
            RailInstruction current = updateStatusToSent(instruction);
            simulateDelay();

            boolean success = random.nextDouble() >= failureRate;

            if (success) {
                handleSuccess(current);
            } else {
                handleFailure(current);
            }
        } catch (Exception ex) {
            log.error("Unhandled error processing rail instruction id={}: {}",
                    instruction.getId(), ex.getMessage(), ex);
        }
    }

    @Transactional
    protected RailInstruction updateStatusToSent(RailInstruction instruction) {
        // Re-fetch by id so we operate on a managed entity with the current DB version,
        // avoiding optimistic-lock conflicts when the caller passed a detached reference.
        RailInstruction fresh = railInstructionRepository.findById(instruction.getId())
                .orElse(instruction);
        fresh.setRailStatus(RailStatus.SENT);
        fresh.setSentAt(LocalDateTime.now());
        RailInstruction saved = railInstructionRepository.save(fresh);
        log.info("Instruction id={} status updated to SENT", saved.getId());
        return saved;
    }

    @Transactional
    protected RailInstruction handleSuccess(RailInstruction instruction) {
        RailInstruction fresh = railInstructionRepository.findById(instruction.getId())
                .orElse(instruction);
        fresh.setRailStatus(RailStatus.SETTLED);
        fresh.setCompletedAt(LocalDateTime.now());
        RailInstruction saved = railInstructionRepository.save(fresh);
        log.info("Instruction id={} settled successfully", saved.getId());

        callbackPaymentService(saved.getPaymentId(), "COMPLETED",
                "Rail settlement completed via " + saved.getRail(),
                saved.getRail().name());
        return saved;
    }

    @Transactional
    protected RailInstruction handleFailure(RailInstruction instruction) {
        RailInstruction fresh = railInstructionRepository.findById(instruction.getId())
                .orElse(instruction);
        fresh.setRetryCount(fresh.getRetryCount() + 1);
        String failureReason = "Simulated rail failure for " + fresh.getRail();
        fresh.setFailureReason(failureReason);

        if (fresh.getRetryCount() < fresh.getMaxRetries()) {
            log.warn("Instruction id={} failed (attempt {}/{}). Retrying...",
                    fresh.getId(), fresh.getRetryCount(), fresh.getMaxRetries());

            fresh.setRailStatus(RailStatus.PENDING);
            RailInstruction saved = railInstructionRepository.save(fresh);

            exponentialBackoff(saved.getRetryCount());
            processInstruction(saved);
            return saved;
        } else {
            log.error("Instruction id={} failed after {} retries. Marking as FAILED.",
                    fresh.getId(), fresh.getMaxRetries());

            fresh.setRailStatus(RailStatus.FAILED);
            fresh.setCompletedAt(LocalDateTime.now());
            RailInstruction saved = railInstructionRepository.save(fresh);

            callbackPaymentService(saved.getPaymentId(), "FAILED",
                    "Rail processing failed after " + saved.getMaxRetries() + " retries: " + failureReason,
                    saved.getRail().name());
            return saved;
        }
    }

    private void simulateDelay() {
        try {
            int delay = delayMinMs + random.nextInt(delayMaxMs - delayMinMs + 1);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Rail simulation interrupted");
        }
    }

    private void exponentialBackoff(int retryCount) {
        try {
            long backoffMs = (long) Math.pow(2, retryCount) * 1000L;
            log.info("Exponential backoff: waiting {}ms before retry", backoffMs);
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Backoff interrupted");
        }
    }

    private void callbackPaymentService(Long paymentId, String status, String reason, String rail) {
        try {
            PaymentStatusUpdate update = PaymentStatusUpdate.builder()
                    .paymentId(paymentId)
                    .newStatus(status)
                    .reason(reason)
                    .rail(rail)
                    .build();
            paymentServiceClient.updatePaymentStatus(paymentId, update);
            log.info("Payment status callback sent: paymentId={}, status={}, rail={}", paymentId, status, rail);
        } catch (Exception e) {
            log.error("Failed to callback payment-service for paymentId={}: {}", paymentId, e.getMessage());
        }
    }
}
