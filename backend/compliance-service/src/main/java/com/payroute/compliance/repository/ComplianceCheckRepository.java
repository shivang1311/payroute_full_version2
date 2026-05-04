package com.payroute.compliance.repository;

import com.payroute.compliance.entity.CheckResult;
import com.payroute.compliance.entity.ComplianceCheck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplianceCheckRepository extends JpaRepository<ComplianceCheck, Long> {

    List<ComplianceCheck> findByPaymentId(Long paymentId);

    Page<ComplianceCheck> findByPaymentId(Long paymentId, Pageable pageable);

    Page<ComplianceCheck> findByResult(CheckResult result, Pageable pageable);
}
