package com.payroute.compliance.dto.response;

import com.payroute.compliance.entity.HoldStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldResponse {

    private Long id;
    private Long paymentId;
    private String reason;
    private Long placedBy;
    private HoldStatus status;
    private String releaseNotes;
    private Long releasedBy;
    private Long version;
    private LocalDateTime placedAt;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
