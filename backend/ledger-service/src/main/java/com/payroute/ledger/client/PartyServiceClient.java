package com.payroute.ledger.client;

import com.payroute.ledger.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "party-service", configuration = FeignConfig.class)
public interface PartyServiceClient {

    @GetMapping("/api/v1/accounts/{id}")
    ResponseEntity<Map<String, Object>> getAccountById(@PathVariable("id") Long id);

    @GetMapping("/api/v1/accounts/{id}/validate")
    ResponseEntity<Map<String, Object>> validateAccountById(@PathVariable("id") Long id);
}
