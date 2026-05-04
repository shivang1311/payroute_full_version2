package com.payroute.routing.repository;

import com.payroute.routing.entity.RailInstruction;
import com.payroute.routing.entity.RailStatus;
import com.payroute.routing.entity.RailType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RailInstructionRepository extends JpaRepository<RailInstruction, Long> {

    List<RailInstruction> findByPaymentId(Long paymentId);

    Optional<RailInstruction> findByCorrelationRef(String correlationRef);

    Page<RailInstruction> findByRailStatus(RailStatus railStatus, Pageable pageable);

    @Query("SELECT ri FROM RailInstruction ri WHERE " +
            "(:paymentId IS NULL OR ri.paymentId = :paymentId) AND " +
            "(:rail IS NULL OR ri.rail = :rail) AND " +
            "(:status IS NULL OR ri.railStatus = :status)")
    Page<RailInstruction> findWithFilters(
            @Param("paymentId") Long paymentId,
            @Param("rail") RailType rail,
            @Param("status") RailStatus status,
            Pageable pageable);

    @Query("SELECT ri.rail, COUNT(ri) FROM RailInstruction ri " +
            "WHERE ri.createdAt BETWEEN :from AND :to GROUP BY ri.rail")
    List<Object[]> aggregateByRail(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Payment IDs of rail instructions matching a given rail + status within a completion window.
     * Used by settlement-service to build settlement batches from real data.
     */
    @Query("SELECT ri.paymentId FROM RailInstruction ri " +
            "WHERE ri.rail = :rail AND ri.railStatus = :status " +
            "AND ri.completedAt BETWEEN :from AND :to")
    List<Long> findPaymentIdsByRailAndStatusAndCompletedBetween(
            @Param("rail") RailType rail,
            @Param("status") RailStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Distinct payment IDs of SETTLED rail instructions completed on a given date.
     * Used by exception-service reconciliation to build the rail-side set.
     */
    @Query("SELECT DISTINCT ri.paymentId FROM RailInstruction ri " +
            "WHERE ri.railStatus = com.payroute.routing.entity.RailStatus.SETTLED " +
            "AND ri.completedAt BETWEEN :from AND :to")
    List<Long> findSettledPaymentIdsOnDate(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Open rail instructions (not yet terminal) with a sent_at older than the given cutoff.
     * Used by the SLA breach scanner.
     */
    @Query("SELECT ri FROM RailInstruction ri WHERE ri.rail = :rail " +
            "AND ri.railStatus IN (com.payroute.routing.entity.RailStatus.PENDING, " +
            "com.payroute.routing.entity.RailStatus.SENT, " +
            "com.payroute.routing.entity.RailStatus.ACKNOWLEDGED) " +
            "AND ri.sentAt IS NOT NULL AND ri.sentAt < :cutoff " +
            "AND ri.breachNotifiedAt IS NULL")
    List<RailInstruction> findBreachedOpenInstructions(
            @Param("rail") RailType rail,
            @Param("cutoff") LocalDateTime cutoff);

    /**
     * Count SLA breaches in a given window — instructions that either completed after the
     * per-rail target or are still open beyond target.
     */
    @Query("SELECT COUNT(ri) FROM RailInstruction ri WHERE ri.breachNotifiedAt BETWEEN :from AND :to")
    Long countBreachesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
