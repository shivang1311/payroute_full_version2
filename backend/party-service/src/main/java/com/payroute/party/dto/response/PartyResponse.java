package com.payroute.party.dto.response;

import com.payroute.party.entity.PartyStatus;
import com.payroute.party.entity.PartyType;
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
@Schema(description = "Party response")
public class PartyResponse {

    @Schema(description = "Party ID", example = "1")
    private Long id;

    @Schema(description = "Party name", example = "Acme Corporation")
    private String name;

    @Schema(description = "Party type", example = "CORPORATE")
    private PartyType type;

    @Schema(description = "Phone number", example = "9876543210")
    private String phone;

    @Schema(description = "Country code", example = "USA")
    private String country;

    @Schema(description = "Risk rating", example = "LOW")
    private String riskRating;

    @Schema(description = "Party status", example = "ACTIVE")
    private PartyStatus status;

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
