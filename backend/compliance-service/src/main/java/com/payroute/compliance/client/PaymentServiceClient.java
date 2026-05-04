package com.payroute.compliance.client;

import com.payroute.compliance.config.FeignConfig;
import com.payroute.compliance.dto.client.PaymentStatusUpdate;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "payment-service", configuration = FeignConfig.class)
public interface PaymentServiceClient {

    @PutMapping("/api/v1/payments/{id}/status")
    ResponseEntity<Map<String, Object>> updatePaymentStatus(
            @PathVariable("id") Long id,
            @RequestBody PaymentStatusUpdate statusUpdate);

    /** Asks payment-service to resume orchestration (routing → processing → completion)
     *  after a compliance hold is released. */
    @PostMapping("/api/v1/payments/{id}/resume-after-hold")
    ResponseEntity<Map<String, Object>> resumeAfterHold(@PathVariable("id") Long id);
}
