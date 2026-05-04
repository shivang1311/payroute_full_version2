package com.payroute.payment.dto.client;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplianceScreenResponse {

    private Long paymentId;
    private String overallResult; // CLEAR, FLAG, HOLD
    private List<ComplianceCheck> checks;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ComplianceCheck {
        private String checkName;
        private String result;
        private String detail;
    }
}
