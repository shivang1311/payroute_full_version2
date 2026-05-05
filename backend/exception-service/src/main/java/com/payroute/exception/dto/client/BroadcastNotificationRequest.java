package com.payroute.exception.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirror of notification-service's BroadcastRequest — fan-out notification
 * fired to every active user with the given role.
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
