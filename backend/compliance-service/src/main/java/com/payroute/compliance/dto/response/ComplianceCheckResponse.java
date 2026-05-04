package com.payroute.compliance.dto.response;

import com.payroute.compliance.entity.CheckResult;
import com.payroute.compliance.entity.CheckSeverity;
import com.payroute.compliance.entity.CheckType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCheckResponse {

    private Long id;
    private Long paymentId;
    private CheckType checkType;
    private CheckSeverity severity;
    private CheckResult result;
    private String details;
    private String checkedBy;
    private LocalDateTime checkedAt;
    private LocalDateTime createdAt;
}
