package com.payroute.compliance.repository;

import com.payroute.compliance.entity.Hold;
import com.payroute.compliance.entity.HoldStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoldRepository extends JpaRepository<Hold, Long> {

    List<Hold> findByPaymentId(Long paymentId);

    Page<Hold> findByPaymentId(Long paymentId, Pageable pageable);

    Page<Hold> findByStatus(HoldStatus status, Pageable pageable);

    Optional<Hold> findByPaymentIdAndStatus(Long paymentId, HoldStatus status);
}
