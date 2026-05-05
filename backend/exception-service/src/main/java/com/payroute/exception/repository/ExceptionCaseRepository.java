package com.payroute.exception.repository;

import com.payroute.exception.entity.ExceptionCase;
import com.payroute.exception.entity.ExceptionCategory;
import com.payroute.exception.entity.ExceptionPriority;
import com.payroute.exception.entity.ExceptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    Page<ExceptionCase> findByStatusAndCategory(ExceptionStatus status, ExceptionCategory category, Pageable pageable);

    Page<ExceptionCase> findByCategoryAndOwnerId(ExceptionCategory category, Long ownerId, Pageable pageable);

    Page<ExceptionCase> findByStatusAndCategoryAndOwnerId(ExceptionStatus status, ExceptionCategory category, Long ownerId, Pageable pageable);

    // --- Stats queries ---

    /** Returns rows of [ExceptionStatus, count] for every status that has at least one case. */
    @Query("SELECT e.status, COUNT(e) FROM ExceptionCase e GROUP BY e.status")
    List<Object[]> countByStatus();

    /**
     * Count non-terminal cases whose createdAt is before the given threshold,
     * i.e. cases that have exceeded the SLA window.
     */
    @Query("SELECT COUNT(e) FROM ExceptionCase e " +
            "WHERE e.status NOT IN :excludedStatuses AND e.createdAt < :threshold")
    long countSlaBreached(
            @Param("excludedStatuses") List<ExceptionStatus> excludedStatuses,
            @Param("threshold") LocalDateTime threshold);
}
