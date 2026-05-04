package com.payroute.party.dto.response;

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
@Schema(description = "Account directory response")
public class AccountResponse {

    @Schema(description = "Account ID", example = "1")
    private Long id;

    @Schema(description = "Owning party ID", example = "1")
    private Long partyId;

    @Schema(description = "Owning party name", example = "Acme Corporation")
    private String partyName;

    @Schema(description = "Account number", example = "1234567890")
    private String accountNumber;

    @Schema(description = "IFSC or IBAN code", example = "SBIN0001234")
    private String ifscIban;

    @Schema(description = "Account alias", example = "main-ops-account")
    private String alias;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @Schema(description = "Account type", example = "SAVINGS")
    private String accountType;

    @Schema(description = "UPI ID (INR) or VPA ID (other currencies)", example = "user@okbank")
    private String vpaUpiId;

    @Schema(description = "Phone-number alias for this account", example = "+919876543210")
    private String phone;

    @Schema(description = "Email alias for this account", example = "ops@example.com")
    private String email;

    @Schema(description = "Whether the account is active", example = "true")
    private boolean active;

    @Schema(description = "Version for optimistic locking")
    private Long version;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

    @Schema(description = "Created by user")
    private String createdBy;

    @Schema(description = "Last updated by user")
    private String updatedBy;
}
