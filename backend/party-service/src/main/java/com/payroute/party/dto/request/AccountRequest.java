package com.payroute.party.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Account directory creation/update request")
public class AccountRequest {

    @NotNull(message = "Party ID is required")
    @Schema(description = "ID of the owning party", example = "1")
    private Long partyId;

    @NotBlank(message = "Account number is required")
    @Size(min = 16, max = 16, message = "Account number must be exactly 16 digits")
    @Pattern(regexp = "^\\d{16}$", message = "Account number must contain only digits")
    @Schema(description = "16-digit account number", example = "1234567890123456")
    private String accountNumber;

    @NotBlank(message = "IFSC/IBAN is required")
    @Schema(description = "IFSC code (for INR) or IBAN/SWIFT (for other currencies)", example = "SBIN0001234")
    private String ifscIban;

    @Schema(description = "Account alias for quick lookup", example = "main-ops-account")
    private String alias;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    @Schema(description = "Currency code (ISO 4217)", example = "INR")
    private String currency;

    @NotBlank(message = "Account type is required")
    @Schema(description = "Account type (SAVINGS, CURRENT, SALARY, NRE, NRO, OVERDRAFT, LOAN, FIXED_DEPOSIT)", example = "SAVINGS")
    private String accountType;

    @Schema(description = "UPI ID (for INR) or VPA ID (for other currencies)", example = "user@okbank")
    private String vpaUpiId;

    @Schema(description = "Phone-number alias for this account", example = "+919876543210")
    private String phone;

    @Schema(description = "Email alias for this account", example = "ops@example.com")
    private String email;

    @Schema(description = "Account active status (for updates)", example = "true")
    private Boolean active;
}
