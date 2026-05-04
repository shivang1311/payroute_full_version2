package com.payroute.exception.client;

import com.payroute.exception.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Map;

@FeignClient(name = "routing-service", configuration = FeignConfig.class)
public interface RoutingServiceClient {

    @GetMapping("/api/v1/routing/stats/settled-on")
    ResponseEntity<Map<String, Object>> settledOn(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date);
}
