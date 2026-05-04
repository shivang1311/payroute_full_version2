package com.payroute.party.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Account validation response used by payment-service via Feign")
public class AccountValidationResponse {

    @Schema(description = "Whether the account exists", example = "true")
    private boolean exists;

    @Schema(description = "Whether the account is active", example = "true")
    private boolean active;

    @Schema(description = "Account currency", example = "USD")
    private String currency;

    @Schema(description = "Name of the party owning this account", example = "Acme Corporation")
    private String partyName;

    @Schema(description = "Account ID", example = "1")
    private Long accountId;

    @Schema(description = "ID of the party owning this account", example = "10")
    private Long partyId;
}
