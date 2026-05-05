package com.payroute.party.client;

import com.payroute.party.config.FeignConfig;
import com.payroute.party.dto.client.LedgerPostRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/** Used to seed an opening balance entry on newly created INR accounts. */
@FeignClient(name = "ledger-service", configuration = FeignConfig.class)
public interface LedgerServiceClient {

    @PostMapping("/api/v1/ledger/entries")
    ResponseEntity<Map<String, Object>> postEntry(@RequestBody LedgerPostRequest request);
}
