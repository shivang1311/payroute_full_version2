package com.payroute.routing.dto.response;

import com.payroute.routing.entity.RailStatus;
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
@Schema(description = "Rail instruction response")
public class RailInstructionResponse {

    @Schema(description = "Instruction ID", example = "1")
    private Long id;

    @Schema(description = "Payment ID", example = "1001")
    private Long paymentId;

    @Schema(description = "Selected rail", example = "RTGS")
    private RailType rail;

    @Schema(description = "Correlation reference", example = "ROUTE-1001-abc123")
    private String correlationRef;

    @Schema(description = "Rail status", example = "PENDING")
    private RailStatus railStatus;

    @Schema(description = "Number of retries attempted", example = "0")
    private Integer retryCount;

    @Schema(description = "Maximum retries allowed", example = "3")
    private Integer maxRetries;

    @Schema(description = "Timestamp when instruction was sent to rail")
    private LocalDateTime sentAt;

    @Schema(description = "Timestamp when instruction was completed")
    private LocalDateTime completedAt;

    @Schema(description = "Failure reason if applicable")
    private String failureReason;

    @Schema(description = "Entity version for optimistic locking")
    private Long version;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
