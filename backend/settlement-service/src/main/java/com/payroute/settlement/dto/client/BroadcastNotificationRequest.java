package com.payroute.settlement.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirror of notification-service's BroadcastRequest — one notification row per
 * active user with the given role. Used here when a settlement batch fails so
 * every reconciliation analyst sees it on their dashboard immediately.
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
