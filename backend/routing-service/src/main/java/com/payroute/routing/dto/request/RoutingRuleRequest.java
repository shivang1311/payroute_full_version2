package com.payroute.routing.dto.request;

import com.payroute.routing.entity.RailType;
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
@Schema(description = "Request to create or update a routing rule")
public class RoutingRuleRequest {

    @NotBlank(message = "Name is required")
    @Schema(description = "Rule name", example = "High Value RTGS Rule")
    private String name;

    @Schema(description = "Rule description", example = "Route payments >= 200000 via RTGS")
    private String description;

    @NotNull(message = "Condition JSON is required")
    @Schema(description = "Rule condition in JSON format",
            example = "{\"field\": \"amount\", \"op\": \"gte\", \"value\": 200000}")
    private String conditionJson;

    @NotNull(message = "Preferred rail is required")
    @Schema(description = "Preferred payment rail", example = "RTGS")
    private RailType preferredRail;

    @Schema(description = "Rule priority (lower value = higher priority)", example = "1")
    private Integer priority;
}
