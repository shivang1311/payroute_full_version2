package com.payroute.settlement.repository;

import com.payroute.settlement.entity.PaymentReport;
import com.payroute.settlement.entity.ReportScope;
import com.payroute.settlement.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentReportRepository extends JpaRepository<PaymentReport, Long> {

    Page<PaymentReport> findByScope(ReportScope scope, Pageable pageable);

    Page<PaymentReport> findByStatus(ReportStatus status, Pageable pageable);
}
