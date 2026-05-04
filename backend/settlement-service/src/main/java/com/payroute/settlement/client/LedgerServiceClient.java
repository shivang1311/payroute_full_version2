package com.payroute.settlement.client;

import com.payroute.settlement.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "ledger-service", configuration = FeignConfig.class)
public interface LedgerServiceClient {

    @GetMapping("/api/v1/ledger/entries")
    ResponseEntity<Map<String, Object>> getEntries(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "100") int size);

    @GetMapping("/api/v1/ledger/fees")
    ResponseEntity<Map<String, Object>> getFeeSchedules(
            @RequestParam(value = "rail", required = false) String rail);

    @GetMapping("/api/v1/ledger/stats/fees-for-payments")
    ResponseEntity<Map<String, Object>> feesForPayments(
            @RequestParam("ids") java.util.List<Long> ids);
}
