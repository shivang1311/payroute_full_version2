package com.payroute.notification.dto.request;

import com.payroute.notification.entity.NotificationCategory;
import com.payroute.notification.entity.NotificationSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fan-out notification — one row will be created per active user with the given role.
 * Used for things like "new compliance hold arrived" → all COMPLIANCE analysts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastRequest {

    /** Role to broadcast to, e.g. {@code COMPLIANCE}. */
    @NotBlank(message = "Role is required")
    private String role;

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
