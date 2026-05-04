package com.payroute.ledger.config;

import com.payroute.ledger.entity.FeeSchedule;
import com.payroute.ledger.entity.FeeType;
import com.payroute.ledger.entity.RailType;
import com.payroute.ledger.repository.FeeScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ensures the canonical PayRoute Hub fee rates are present and up to date
 * each time the ledger-service starts. Idempotent: if a matching active row
 * already exists, it's overwritten only if its values differ.
 *
 *   IMPS  → 0.40 % of amount (PERCENTAGE)
 *   NEFT  → 0.25 %
 *   RTGS  → 0.10 %
 *   BOOK  →   FLAT 0  (internal book transfer, no fee)
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class FeeRateBootstrap {

    private static final String PRODUCT = "PAYMENT";

    private final FeeScheduleRepository repo;

    @Bean
    public ApplicationRunner ensureCanonicalFeeRates() {
        return args -> {
            upsert(RailType.IMPS, FeeType.PERCENTAGE, new BigDecimal("0.40"));
            upsert(RailType.NEFT, FeeType.PERCENTAGE, new BigDecimal("0.25"));
            upsert(RailType.RTGS, FeeType.PERCENTAGE, new BigDecimal("0.10"));
            upsert(RailType.BOOK, FeeType.FLAT,       new BigDecimal("0.00"));
        };
    }

    @Transactional
    public void upsert(RailType rail, FeeType feeType, BigDecimal value) {
        List<FeeSchedule> existing = repo.findByProductAndRailAndActiveTrue(PRODUCT, rail);
        if (existing.isEmpty()) {
            FeeSchedule fs = FeeSchedule.builder()
                    .product(PRODUCT)
                    .rail(rail)
                    .feeType(feeType)
                    .value(value)
                    .currency("INR")
                    .effectiveFrom(LocalDate.of(2026, 1, 1))
                    .active(true)
                    .build();
            repo.save(fs);
            log.info("[FeeRateBootstrap] Created {} schedule {} {}", rail, feeType, value);
            return;
        }

        for (FeeSchedule fs : existing) {
            boolean changed = false;
            if (fs.getFeeType() != feeType) { fs.setFeeType(feeType); changed = true; }
            if (fs.getValue() == null || fs.getValue().compareTo(value) != 0) {
                fs.setValue(value);
                changed = true;
            }
            // For PERCENTAGE rails we don't want a flat min/max chopping our calculation;
            // null them out so the % rate applies cleanly.
            if (feeType == FeeType.PERCENTAGE) {
                if (fs.getMinFee() != null) { fs.setMinFee(null); changed = true; }
                if (fs.getMaxFee() != null) { fs.setMaxFee(null); changed = true; }
            }
            if (changed) {
                repo.save(fs);
                log.info("[FeeRateBootstrap] Updated {} schedule -> {} {}", rail, feeType, value);
            }
        }
    }
}
