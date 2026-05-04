package com.payroute.ledger.repository;

import com.payroute.ledger.entity.FeeSchedule;
import com.payroute.ledger.entity.FeeType;
import com.payroute.ledger.entity.RailType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeeScheduleRepositoryTest {

    @Autowired FeeScheduleRepository repo;

    @BeforeEach
    void seed() {
        repo.deleteAll();

        // NEFT — active, percentage
        repo.save(FeeSchedule.builder()
                .product("PAYMENT").rail(RailType.NEFT).feeType(FeeType.PERCENTAGE)
                .value(new BigDecimal("0.25")).currency("INR")
                .effectiveFrom(LocalDate.of(2026, 1, 1)).active(true).build());

        // RTGS — also active
        repo.save(FeeSchedule.builder()
                .product("PAYMENT").rail(RailType.RTGS).feeType(FeeType.PERCENTAGE)
                .value(new BigDecimal("0.10")).currency("INR")
                .effectiveFrom(LocalDate.of(2026, 1, 1)).active(true).build());

        // Inactive legacy NEFT row — should be ignored by findActiveSchedule
        repo.save(FeeSchedule.builder()
                .product("PAYMENT").rail(RailType.NEFT).feeType(FeeType.FLAT)
                .value(new BigDecimal("5.00")).currency("INR")
                .effectiveFrom(LocalDate.of(2025, 1, 1))
                .effectiveTo(LocalDate.of(2025, 12, 31)).active(false).build());
    }

    @Nested
    @DisplayName("findActiveSchedule (date + active filter)")
    class FindActive {
        @Test
        void returnsCurrentlyActiveSchedule() {
            Optional<FeeSchedule> result = repo.findActiveSchedule("PAYMENT", RailType.NEFT, LocalDate.of(2026, 6, 1));
            assertThat(result).isPresent();
            assertThat(result.get().getFeeType()).isEqualTo(FeeType.PERCENTAGE);
            assertThat(result.get().getValue()).isEqualByComparingTo("0.25");
        }

        @Test
        void emptyWhenNoMatchingRail() {
            assertThat(repo.findActiveSchedule("PAYMENT", RailType.IMPS, LocalDate.now())).isEmpty();
        }

        @Test
        void emptyWhenDateBeforeEffectiveFrom() {
            assertThat(repo.findActiveSchedule("PAYMENT", RailType.NEFT, LocalDate.of(2024, 1, 1))).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByActiveTrue / findByProduct / findByProductAndRailAndActiveTrue")
    class Lookups {
        @Test
        void findByActiveTrueExcludesInactive() {
            assertThat(repo.findByActiveTrue()).hasSize(2);
        }

        @Test
        void findByProductIncludesInactive() {
            // returns ALL rows for the product, including inactive
            assertThat(repo.findByProduct("PAYMENT")).hasSize(3);
        }

        @Test
        void findByProductAndRailAndActiveTrue() {
            assertThat(repo.findByProductAndRailAndActiveTrue("PAYMENT", RailType.NEFT)).hasSize(1);
            assertThat(repo.findByProductAndRailAndActiveTrue("PAYMENT", RailType.IMPS)).isEmpty();
        }
    }
}
