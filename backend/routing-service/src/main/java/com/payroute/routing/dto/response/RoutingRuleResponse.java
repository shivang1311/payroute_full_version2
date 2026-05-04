package com.payroute.routing.dto.response;

import com.payroute.routing.entity.RailType;
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
@Schema(description = "Routing rule response")
public class RoutingRuleResponse {

    @Schema(description = "Rule ID", example = "1")
    private Long id;

    @Schema(description = "Rule name", example = "High Value RTGS Rule")
    private String name;

    @Schema(description = "Rule description")
    private String description;

    @Schema(description = "Condition JSON")
    private String conditionJson;

    @Schema(description = "Preferred rail", example = "RTGS")
    private RailType preferredRail;

    @Schema(description = "Rule priority", example = "1")
    private Integer priority;

    @Schema(description = "Whether rule is active", example = "true")
    private Boolean active;

    @Schema(description = "Entity version")
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
