package com.payroute.payment.client;

import com.payroute.payment.config.FeignConfig;
import com.payroute.payment.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Talks to iam-service for things payment-service needs at orchestration time —
 * currently just the customer transaction-PIN check that gates every payment.
 */
@FeignClient(name = "iam-service", configuration = FeignConfig.class)
public interface IamServiceClient {

    /**
     * Verifies a transaction PIN against the stored hash for the given user.
     * Service-to-service variant — the userId is in the path so this works
     * regardless of which JWT-derived headers payment-service is propagating.
     *
     * Response shape: {"success":true,"data":{"valid":true|false}, ...}
     */
    @PostMapping("/api/v1/users/{id}/pin/verify")
    ApiResponse<Map<String, Boolean>> verifyTransactionPin(
            @PathVariable("id") Long userId,
            @RequestBody Map<String, String> body);
}
