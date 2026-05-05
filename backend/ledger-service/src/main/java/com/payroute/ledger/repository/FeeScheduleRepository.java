package com.payroute.ledger.repository;

import com.payroute.ledger.entity.FeeSchedule;
import com.payroute.ledger.entity.RailType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository over {@link FeeSchedule}. The headline lookup
 * {@code findActiveSchedule} returns the row that is active on a given date
 * for a given (product, rail) pair — used by
 * {@link com.payroute.ledger.service.FeeService} on every payment posting.
 */
@Repository
public interface FeeScheduleRepository extends JpaRepository<FeeSchedule, Long> {

    @Query("SELECT fs FROM FeeSchedule fs WHERE fs.product = :product AND fs.rail = :rail " +
            "AND fs.active = true AND fs.effectiveFrom <= :date " +
            "AND (fs.effectiveTo >= :date OR fs.effectiveTo IS NULL)")
    Optional<FeeSchedule> findActiveSchedule(
            @Param("product") String product,
            @Param("rail") RailType rail,
            @Param("date") LocalDate date);

    List<FeeSchedule> findByProductAndRailAndActiveTrueAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualOrEffectiveToIsNull(
            String product, RailType rail, LocalDate effectiveFrom, LocalDate effectiveTo);

    List<FeeSchedule> findByActiveTrue();

    List<FeeSchedule> findByProduct(String product);

    List<FeeSchedule> findByProductAndRailAndActiveTrue(String product, RailType rail);
}
