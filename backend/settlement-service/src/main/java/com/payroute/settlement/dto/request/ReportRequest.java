package com.payroute.settlement.dto.request;

import com.payroute.settlement.entity.ReportScope;
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
public class ReportRequest {

    @NotBlank(message = "Report name is required")
    private String reportName;

    @NotNull(message = "Scope is required")
    private ReportScope scope;

    private String parameters;
}
