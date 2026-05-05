package com.payroute.compliance.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wire shape that mirrors notification-service's {@code BroadcastRequest}.
 * One row per active user with the given role gets created on the receiving end.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastNotificationRequest {
    private String role;
    private String title;
    private String message;
    private String category;
    private String severity;
    private String referenceType;
    private Long referenceId;
}
