package com.payroute.iam.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "party-service", path = "/api/v1/parties")
public interface PartyServiceClient {

    @PostMapping
    Map<String, Object> createParty(@RequestBody Map<String, Object> request);

    @GetMapping("/{id}")
    Map<String, Object> getParty(@PathVariable("id") Long id);

    @PutMapping("/{id}")
    Map<String, Object> updateParty(@PathVariable("id") Long id, @RequestBody Map<String, Object> request);
}
