package com.payroute.compliance.dto.response;

import com.payroute.compliance.entity.CheckResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceScreenResponse {

    private Long paymentId;
    private CheckResult overallResult;
    private List<ComplianceCheckResponse> checks;
}
