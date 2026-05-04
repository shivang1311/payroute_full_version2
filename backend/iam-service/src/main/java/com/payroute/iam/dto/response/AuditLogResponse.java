package com.payroute.iam.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Audit log response payload")
public class AuditLogResponse {

    @Schema(description = "Audit log ID", example = "1")
    private Long id;

    @Schema(description = "User ID", example = "1")
    private Long userId;

    @Schema(description = "Username", example = "john.doe")
    private String username;

    @Schema(description = "Action performed", example = "LOGIN")
    private String action;

    @Schema(description = "Entity type", example = "USER")
    private String entityType;

    @Schema(description = "Entity ID", example = "42")
    private String entityId;

    @Schema(description = "Additional details in JSON format")
    private String details;

    @Schema(description = "IP address of the request", example = "192.168.1.1")
    private String ipAddress;

    @Schema(description = "Timestamp of the action")
    private LocalDateTime createdAt;
}
