package com.payroute.routing.client;

import com.payroute.routing.config.FeignConfig;
import com.payroute.routing.dto.request.PaymentStatusUpdate;
import com.payroute.routing.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", configuration = FeignConfig.class)
public interface PaymentServiceClient {

    @PutMapping("/api/v1/payments/{id}/status")
    ApiResponse<Void> updatePaymentStatus(@PathVariable("id") Long id,
                                          @RequestBody PaymentStatusUpdate statusUpdate);
}
