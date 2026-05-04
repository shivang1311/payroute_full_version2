package com.payroute.exception.repository;

import com.payroute.exception.entity.ReturnItem;
import com.payroute.exception.entity.ReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReturnItemRepository extends JpaRepository<ReturnItem, Long> {

    Page<ReturnItem> findByPaymentId(Long paymentId, Pageable pageable);

    Page<ReturnItem> findByStatus(ReturnStatus status, Pageable pageable);

    List<ReturnItem> findByPaymentId(Long paymentId);
}
