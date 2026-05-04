package com.payroute.routing.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    private Long userId;
    private String title;
    private String message;
    private String category;
    private String severity;
    private String referenceType;
    private Long referenceId;
}
