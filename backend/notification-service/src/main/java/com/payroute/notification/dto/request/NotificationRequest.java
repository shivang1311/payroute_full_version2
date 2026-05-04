package com.payroute.notification.dto.request;

import com.payroute.notification.entity.NotificationCategory;
import com.payroute.notification.entity.NotificationSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "Title is required")
    private String title;

    private String message;

    @NotNull(message = "Category is required")
    private NotificationCategory category;

    @NotNull(message = "Severity is required")
    private NotificationSeverity severity;

    private String referenceType;

    private Long referenceId;
}
