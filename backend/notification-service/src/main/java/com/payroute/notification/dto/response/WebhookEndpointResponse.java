package com.payroute.notification.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEndpointResponse {
    private Long id;
    private Long userId;
    private String name;
    private String url;
    // Secret is returned only on creation; later calls return a masked value
    private String secret;
    private String events;
    private Boolean active;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastFailureAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
