package com.payroute.party.dto.request;

import com.payroute.party.entity.PartyStatus;
import com.payroute.party.entity.PartyType;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Party creation/update request")
public class PartyRequest {

    @NotBlank(message = "Name is required")
    @Schema(description = "Party name", example = "Acme Corporation")
    private String name;

    @NotNull(message = "Party type is required")
    @Schema(description = "Party type", example = "CORPORATE")
    private PartyType type;

    @Schema(description = "Phone number (10 digits)", example = "9876543210")
    private String phone;

    @Schema(description = "Country code (ISO 3166-1 alpha-3)", example = "USA")
    private String country;

    @Schema(description = "Risk rating", example = "LOW")
    private String riskRating;

    @Schema(description = "Party status (for updates)", example = "ACTIVE")
    private PartyStatus status;
}
