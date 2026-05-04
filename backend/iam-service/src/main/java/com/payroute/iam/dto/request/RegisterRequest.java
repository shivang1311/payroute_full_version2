package com.payroute.iam.dto.request;

import com.payroute.iam.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User registration request payload")
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Schema(description = "Full name (used as party name for customers)", example = "John Doe")
    private String name;

    @NotBlank(message = "Username is required")
    @Schema(description = "Username (login ID)", example = "john.doe")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Schema(description = "Email address", example = "john.doe@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d]).{8,}$",
        message = "Password must have at least 1 uppercase letter, 1 lowercase letter, 1 digit, and 1 special character"
    )
    @Schema(description = "Password (min 8 chars, must include uppercase, lowercase, digit, special char)", example = "SecurePass@1")
    private String password;

    @NotBlank(message = "Phone number is required")
    @Size(min = 10, max = 10, message = "Phone number must be exactly 10 digits")
    @Pattern(regexp = "^\\d{10}$", message = "Phone number must contain only digits")
    @Schema(description = "Phone number (10 digits)", example = "9876543210")
    private String phone;

    @Schema(description = "User role", example = "CUSTOMER")
    private Role role;
}
