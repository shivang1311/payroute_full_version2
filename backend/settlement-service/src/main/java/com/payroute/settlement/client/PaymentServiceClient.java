package com.payroute.settlement.client;

import com.payroute.settlement.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "payment-service", configuration = FeignConfig.class)
public interface PaymentServiceClient {

    @GetMapping("/api/v1/payments/aggregate")
    ResponseEntity<Map<String, Object>> aggregateByIds(@RequestParam("ids") List<Long> ids);
}
