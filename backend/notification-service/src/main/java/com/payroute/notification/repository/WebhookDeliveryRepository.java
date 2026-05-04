package com.payroute.notification.repository;

import com.payroute.notification.entity.DeliveryStatus;
import com.payroute.notification.entity.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    Page<WebhookDelivery> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<WebhookDelivery> findByEndpointIdOrderByCreatedAtDesc(Long endpointId, Pageable pageable);

    @Query("SELECT d FROM WebhookDelivery d WHERE d.status IN :statuses AND (d.nextAttemptAt IS NULL OR d.nextAttemptAt <= :now) ORDER BY d.id ASC")
    List<WebhookDelivery> findDue(@Param("statuses") List<DeliveryStatus> statuses,
                                  @Param("now") LocalDateTime now,
                                  Pageable pageable);
}
