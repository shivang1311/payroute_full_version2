package com.payroute.iam.dto.response;

import com.payroute.iam.entity.Role;
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
@Schema(description = "User response payload")
public class UserResponse {

    @Schema(description = "User ID", example = "1")
    private Long id;

    @Schema(description = "Username", example = "john.doe")
    private String username;

    @Schema(description = "Email address", example = "john.doe@example.com")
    private String email;

    @Schema(description = "Phone number", example = "+1234567890")
    private String phone;

    @Schema(description = "User role", example = "CUSTOMER")
    private Role role;

    @Schema(description = "Whether the user is active", example = "true")
    private boolean active;

    @Schema(description = "True if user must change password on next login", example = "false")
    private boolean mustChangePassword;

    @Schema(description = "True if the transaction PIN has been set (CUSTOMER only)", example = "true")
    private boolean pinSet;

    @Schema(description = "Linked party ID (for CUSTOMER users)", example = "1")
    private Long partyId;

    @Schema(description = "Account creation timestamp")
    private LocalDateTime createdAt;
}
