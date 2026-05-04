package com.payroute.payment.client;

import com.payroute.payment.config.FeignConfig;
import com.payroute.payment.dto.client.ExceptionCaseRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "exception-service", configuration = FeignConfig.class)
public interface ExceptionServiceClient {

    /**
     * Create an exception case. Used by PaymentOrchestrator to auto-raise a case
     * whenever a payment transitions into a failure state.
     */
    @PostMapping("/api/v1/exceptions")
    void createException(@RequestBody ExceptionCaseRequest request);

    /**
     * Auto-close all open exception cases linked to a payment. Called when the
     * payment is retried so the queue cleans itself up.
     */
    @PutMapping("/api/v1/exceptions/payment/{paymentId}/auto-close")
    void autoCloseForPayment(@PathVariable("paymentId") Long paymentId,
                             @RequestParam(value = "reason", required = false) String reason);
}
