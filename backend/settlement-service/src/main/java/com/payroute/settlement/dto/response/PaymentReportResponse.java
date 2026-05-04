package com.payroute.settlement.dto.response;

import com.payroute.settlement.entity.ReportScope;
import com.payroute.settlement.entity.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReportResponse {

    private Long id;
    private String reportName;
    private ReportScope scope;
    private String parameters;
    private String metrics;
    private ReportStatus status;
    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
    private String createdBy;
}
