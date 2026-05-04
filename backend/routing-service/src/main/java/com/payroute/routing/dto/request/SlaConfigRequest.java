package com.payroute.routing.dto.request;

import com.payroute.routing.entity.RailType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaConfigRequest {

    @NotNull(message = "Rail is required")
    private RailType rail;

    @NotNull(message = "targetTatSeconds is required")
    @Min(value = 1, message = "targetTatSeconds must be >= 1")
    private Integer targetTatSeconds;

    @NotNull(message = "warningThresholdSeconds is required")
    @Min(value = 1, message = "warningThresholdSeconds must be >= 1")
    private Integer warningThresholdSeconds;

    private Boolean active;
}
