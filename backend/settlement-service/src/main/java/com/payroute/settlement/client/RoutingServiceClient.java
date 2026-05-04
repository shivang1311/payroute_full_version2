package com.payroute.settlement.client;

import com.payroute.settlement.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Map;

@FeignClient(name = "routing-service", configuration = FeignConfig.class)
public interface RoutingServiceClient {

    @GetMapping("/api/v1/routing/instructions")
    ResponseEntity<Map<String, Object>> getInstructions(
            @RequestParam(value = "rail", required = false) String rail,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "100") int size);

    @GetMapping("/api/v1/routing/stats/settlement-eligible")
    ResponseEntity<Map<String, Object>> settlementEligible(
            @RequestParam("rail") String rail,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to);
}
