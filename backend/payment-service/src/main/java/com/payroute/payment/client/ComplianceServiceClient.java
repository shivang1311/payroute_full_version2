package com.payroute.payment.client;

import com.payroute.payment.config.FeignConfig;
import com.payroute.payment.dto.client.ComplianceScreenRequest;
import com.payroute.payment.dto.client.ComplianceScreenResponse;
import com.payroute.payment.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "compliance-service", configuration = FeignConfig.class)
public interface ComplianceServiceClient {

    @PostMapping("/api/v1/compliance/screen")
    ApiResponse<ComplianceScreenResponse> screen(@RequestBody ComplianceScreenRequest request);
}
