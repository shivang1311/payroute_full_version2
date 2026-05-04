package com.payroute.settlement.repository;

import com.payroute.settlement.entity.BatchStatus;
import com.payroute.settlement.entity.RailType;
import com.payroute.settlement.entity.SettlementBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, Long> {

    Page<SettlementBatch> findByRail(RailType rail, Pageable pageable);

    Page<SettlementBatch> findByStatus(BatchStatus status, Pageable pageable);

    Page<SettlementBatch> findByRailAndStatus(RailType rail, BatchStatus status, Pageable pageable);

    List<SettlementBatch> findByRailAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqual(
            RailType rail, LocalDateTime periodStart, LocalDateTime periodEnd);
}
