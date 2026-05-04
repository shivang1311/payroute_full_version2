package com.payroute.payment.client;

import com.payroute.payment.config.FeignConfig;
import com.payroute.payment.dto.client.RailInstructionResponse;
import com.payroute.payment.dto.client.RouteRequest;
import com.payroute.payment.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "routing-service", configuration = FeignConfig.class)
public interface RoutingServiceClient {

    @PostMapping("/api/v1/routing/route")
    ApiResponse<RailInstructionResponse> routePayment(@RequestBody RouteRequest request);
}
