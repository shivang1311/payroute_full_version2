package com.payroute.payment.dto.client;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEventRequest {
    // e.g. PAYMENT_COMPLETED
    private String eventType;
    private Long referenceId;
    private Long userId; // optional: null = fan-out to all
    private Map<String, Object> data;
}
