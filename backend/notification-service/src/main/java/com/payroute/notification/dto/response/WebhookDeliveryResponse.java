package com.payroute.notification.dto.response;

import com.payroute.notification.entity.DeliveryStatus;
import com.payroute.notification.entity.WebhookEventType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDeliveryResponse {
    private Long id;
    private Long endpointId;
    private String endpointUrl;
    private WebhookEventType eventType;
    private Long referenceId;
    private String payload;
    private DeliveryStatus status;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime lastAttemptAt;
    private Integer responseStatus;
    private String responseBody;
    private LocalDateTime createdAt;
}
