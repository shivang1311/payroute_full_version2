package com.payroute.payment.service;

import com.payroute.payment.entity.ScheduledPayment;
import com.payroute.payment.entity.ScheduledPaymentStatus;
import com.payroute.payment.repository.ScheduledPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Polls every 30 seconds for ACTIVE schedules whose {@code nextRunAt} has elapsed
 * and fires each one via {@link ScheduledPaymentService#fireOne(Long)} (its own transaction).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledPaymentWorker {

    private final ScheduledPaymentRepository repository;
    private final ScheduledPaymentService scheduledPaymentService;

    @Scheduled(fixedDelayString = "${payroute.scheduled-payment.poll-ms:30000}",
            initialDelayString = "${payroute.scheduled-payment.initial-delay-ms:15000}")
    public void tick() {
        log.debug("ScheduledPaymentWorker tick");
        List<ScheduledPayment> due = repository.findDue(ScheduledPaymentStatus.ACTIVE, LocalDateTime.now());
        if (due.isEmpty()) return;
        log.info("ScheduledPaymentWorker: {} due schedule(s)", due.size());
        for (ScheduledPayment s : due) {
            try {
                scheduledPaymentService.fireOne(s.getId());
            } catch (Exception ex) {
                log.error("Failed to fire scheduled payment id={}: {}", s.getId(), ex.getMessage(), ex);
            }
        }
    }
}
