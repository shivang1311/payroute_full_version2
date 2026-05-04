package com.payroute.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEndpointRequest {
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 500)
    private String url;

    // Optional on update; auto-generated on create if blank
    @Size(max = 128)
    private String secret;

    // CSV of WebhookEventType names; empty or "*" means all
    @Size(max = 500)
    private String events;

    private Boolean active;
}
