package com.payroute.exception.client;

import com.payroute.exception.config.FeignConfig;
import com.payroute.exception.dto.client.LedgerPostRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "ledger-service", configuration = FeignConfig.class)
public interface LedgerServiceClient {

    @PostMapping("/api/v1/ledger/entries")
    ResponseEntity<Map<String, Object>> postEntry(@RequestBody LedgerPostRequest request);

    @GetMapping("/api/v1/ledger/entries")
    ResponseEntity<Map<String, Object>> getEntries(
            @RequestParam(value = "paymentId", required = false) Long paymentId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "100") int size);

    @PostMapping("/api/v1/ledger/entries/{paymentId}/reverse")
    ResponseEntity<Map<String, Object>> reversePayment(@PathVariable("paymentId") Long paymentId);

    @GetMapping("/api/v1/ledger/stats/payment-ids-on")
    ResponseEntity<Map<String, Object>> paymentIdsOn(
            @RequestParam("date") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date);
}
