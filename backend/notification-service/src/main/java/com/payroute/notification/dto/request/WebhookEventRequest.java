package com.payroute.notification.dto.request;

import com.payroute.notification.entity.WebhookEventType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Map;

/**
 * Sent by payment-service (and others) to enqueue a webhook event.
 * The receiving service fans out to every matching active endpoint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEventRequest {

    @NotNull
    private WebhookEventType eventType;

    // Payment id or equivalent domain reference
    private Long referenceId;

    // Target user — if non-null, only that user's endpoints receive it.
    // If null, the event fans out to all active endpoints that subscribe to the event.
    private Long userId;

    // Arbitrary event-specific fields (amount, status, accounts, etc.)
    private Map<String, Object> data;
}
