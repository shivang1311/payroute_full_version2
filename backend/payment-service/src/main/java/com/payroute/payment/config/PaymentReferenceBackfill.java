package com.payroute.payment.config;

import com.payroute.payment.entity.PaymentOrder;
import com.payroute.payment.repository.PaymentOrderRepository;
import com.payroute.payment.util.PaymentReferenceGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-time backfill: rows that existed before the {@code payment_reference}
 * column was added will have NULL — populate them with the deterministic
 * reference derived from id + createdAt. Idempotent and bounded (500 rows
 * per startup pass) to keep boot fast on large tables.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class PaymentReferenceBackfill {

    private final PaymentOrderRepository repo;

    @Bean
    public ApplicationRunner backfillPaymentReferences() {
        return args -> doBackfill();
    }

    @Transactional
    public void doBackfill() {
        int totalUpdated = 0;
        while (true) {
            List<PaymentOrder> batch = repo.findTop500ByReferenceIsNull();
            if (batch.isEmpty()) break;
            for (PaymentOrder p : batch) {
                p.setReference(PaymentReferenceGenerator.generate(p.getId(), p.getCreatedAt()));
            }
            repo.saveAll(batch);
            totalUpdated += batch.size();
            if (batch.size() < 500) break; // last page
        }
        if (totalUpdated > 0) {
            log.info("Backfilled payment_reference for {} existing payment(s)", totalUpdated);
        }
    }
}
