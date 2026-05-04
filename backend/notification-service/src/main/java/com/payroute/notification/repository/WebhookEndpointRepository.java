package com.payroute.notification.repository;

import com.payroute.notification.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {
    List<WebhookEndpoint> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<WebhookEndpoint> findByActiveTrue();
    Optional<WebhookEndpoint> findFirstByUserIdAndUrl(Long userId, String url);
}
