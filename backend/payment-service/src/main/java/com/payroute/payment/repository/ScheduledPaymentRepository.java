package com.payroute.payment.repository;

import com.payroute.payment.entity.ScheduledPayment;
import com.payroute.payment.entity.ScheduledPaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduledPaymentRepository extends JpaRepository<ScheduledPayment, Long> {

    Page<ScheduledPayment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ScheduledPayment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT s FROM ScheduledPayment s WHERE s.status = :status AND s.nextRunAt IS NOT NULL AND s.nextRunAt <= :now ORDER BY s.nextRunAt ASC")
    List<ScheduledPayment> findDue(@Param("status") ScheduledPaymentStatus status,
                                   @Param("now") LocalDateTime now);
}
