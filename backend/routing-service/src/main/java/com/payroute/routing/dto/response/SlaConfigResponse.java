package com.payroute.routing.dto.response;

import com.payroute.routing.entity.RailType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaConfigResponse {
    private Long id;
    private RailType rail;
    private Integer targetTatSeconds;
    private Integer warningThresholdSeconds;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
