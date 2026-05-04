package com.payroute.payment.client;

import com.payroute.payment.config.FeignConfig;
import com.payroute.payment.dto.client.AccountValidationResponse;
import com.payroute.payment.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "party-service", configuration = FeignConfig.class)
public interface PartyServiceClient {

    @GetMapping("/api/v1/accounts/validate")
    ApiResponse<AccountValidationResponse> validateAccount(
            @RequestParam("accountNumber") String accountNumber,
            @RequestParam("ifscIban") String ifscIban);

    @GetMapping("/api/v1/accounts/{id}/validate")
    ApiResponse<AccountValidationResponse> validateAccountById(@PathVariable("id") Long id);
}
