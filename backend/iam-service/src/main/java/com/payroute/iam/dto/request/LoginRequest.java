package com.payroute.iam.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Login request payload")
public class LoginRequest {

    @NotBlank(message = "Username is required")
    @Schema(description = "Username", example = "john.doe")
    private String username;

    @NotBlank(message = "Password is required")
    @Schema(description = "Password", example = "securePass123")
    private String password;
}
