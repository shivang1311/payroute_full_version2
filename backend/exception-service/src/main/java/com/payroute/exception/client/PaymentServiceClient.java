package com.payroute.exception.client;

import com.payroute.exception.config.FeignConfig;
import com.payroute.exception.dto.client.PaymentStatusUpdate;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "payment-service", configuration = FeignConfig.class)
public interface PaymentServiceClient {

    @PutMapping("/api/v1/payments/{id}/status")
    ResponseEntity<Map<String, Object>> updatePaymentStatus(
            @PathVariable("id") Long id,
            @RequestBody PaymentStatusUpdate statusUpdate);
}
