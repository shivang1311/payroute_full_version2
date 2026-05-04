package com.payroute.exception.repository;

import com.payroute.exception.entity.ExceptionCase;
import com.payroute.exception.entity.ExceptionCategory;
import com.payroute.exception.entity.ExceptionPriority;
import com.payroute.exception.entity.ExceptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExceptionCaseRepository extends JpaRepository<ExceptionCase, Long> {

    Page<ExceptionCase> findByStatus(ExceptionStatus status, Pageable pageable);

    Page<ExceptionCase> findByCategory(ExceptionCategory category, Pageable pageable);

    Page<ExceptionCase> findByPriority(ExceptionPriority priority, Pageable pageable);

    Page<ExceptionCase> findByPaymentId(Long paymentId, Pageable pageable);

    List<ExceptionCase> findByPaymentId(Long paymentId);

    Page<ExceptionCase> findByOwnerId(Long ownerId, Pageable pageable);

    Page<ExceptionCase> findByStatusAndOwnerId(ExceptionStatus status, Long ownerId, Pageable pageable);
}
