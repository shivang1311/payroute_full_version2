package com.payroute.notification.dto.response;

import com.payroute.notification.entity.NotificationCategory;
import com.payroute.notification.entity.NotificationSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private String userId;
    private String title;
    private String message;
    private NotificationCategory category;
    private NotificationSeverity severity;
    private String referenceType;
    private Long referenceId;
    private Boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
