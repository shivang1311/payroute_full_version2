package com.payroute.routing.service;

import com.payroute.routing.client.NotificationServiceClient;
import com.payroute.routing.dto.client.NotificationRequest;
import com.payroute.routing.entity.RailInstruction;
import com.payroute.routing.entity.SlaConfig;
import com.payroute.routing.repository.RailInstructionRepository;
import com.payroute.routing.repository.SlaConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scans open rail instructions for SLA breaches against the per-rail SlaConfig target,
 * fires a SYSTEM/WARNING notification to operations, and marks the instruction as notified
 * so the same breach isn't alerted repeatedly. Implements §2.9 "SLA breach alerts" and
 * §8 Observability "alerting on SLA breaches".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaBreachScanner {

    private static final Long OPS_USER_ID = 0L;   // broadcast to ops; 0 = system channel
    private static final int MAX_PER_SCAN = 100;  // cap per run to avoid flooding

    private final SlaConfigRepository slaConfigRepository;
    private final RailInstructionRepository railInstructionRepository;
    private final NotificationServiceClient notificationServiceClient;

    /**
     * Runs every 60 seconds; first run 30s after boot.
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    @Transactional
    public void scan() {
        List<SlaConfig> configs = slaConfigRepository.findByActiveTrue();
        if (configs.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        int totalBreaches = 0;

        for (SlaConfig cfg : configs) {
            LocalDateTime cutoff = now.minusSeconds(cfg.getTargetTatSeconds());
            List<RailInstruction> breached =
                    railInstructionRepository.findBreachedOpenInstructions(cfg.getRail(), cutoff);
            if (breached.isEmpty()) continue;

            int rail = Math.min(breached.size(), MAX_PER_SCAN);
            for (int i = 0; i < rail; i++) {
                RailInstruction ri = breached.get(i);
                long ageSeconds = java.time.Duration.between(ri.getSentAt(), now).getSeconds();
                try {
                    notificationServiceClient.sendNotification(NotificationRequest.builder()
                            .userId(OPS_USER_ID)
                            .title("SLA breach: " + cfg.getRail() + " payment #" + ri.getPaymentId())
                            .message(String.format(
                                    "Rail instruction %d (payment %d, rail %s) has been open for %ds, exceeding SLA of %ds.",
                                    ri.getId(), ri.getPaymentId(), cfg.getRail(), ageSeconds, cfg.getTargetTatSeconds()))
                            .category("SYSTEM")
                            .severity("WARNING")
                            .referenceType("RAIL_INSTRUCTION")
                            .referenceId(ri.getId())
                            .build());
                    ri.setBreachNotifiedAt(now);
                    totalBreaches++;
                } catch (Exception ex) {
                    log.warn("[SLA-SCAN] failed to notify breach for instruction {}: {}",
                            ri.getId(), ex.getMessage());
                }
            }
            railInstructionRepository.saveAll(breached.subList(0, rail));
        }

        if (totalBreaches > 0) {
            log.info("[SLA-SCAN] flagged {} new SLA breach(es) across {} rail configs",
                    totalBreaches, configs.size());
        }
    }
}
