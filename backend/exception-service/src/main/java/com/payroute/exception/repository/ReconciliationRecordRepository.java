package com.payroute.exception.repository;

import com.payroute.exception.entity.ReconResult;
import com.payroute.exception.entity.ReconciliationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, Long> {

    Page<ReconciliationRecord> findByReconDate(LocalDate reconDate, Pageable pageable);

    Page<ReconciliationRecord> findByResult(ReconResult result, Pageable pageable);

    Page<ReconciliationRecord> findByResolved(Boolean resolved, Pageable pageable);

    List<ReconciliationRecord> findByReconDate(LocalDate reconDate);
}
