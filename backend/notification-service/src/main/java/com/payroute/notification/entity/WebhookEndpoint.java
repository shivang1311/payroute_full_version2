package com.payroute.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_endpoint", indexes = {
        @Index(name = "idx_webhook_user", columnList = "user_id"),
        @Index(name = "idx_webhook_active", columnList = "active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "secret", nullable = false, length = 128)
    private String secret;

    // CSV of WebhookEventType values; empty string or "*" means all
    @Column(name = "events", length = 500)
    private String events;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "last_failure_at")
    private LocalDateTime lastFailureAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
