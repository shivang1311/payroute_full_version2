package com.payroute.payment.client;

import com.payroute.payment.config.FeignConfig;
import com.payroute.payment.dto.client.LedgerEntryResponse;
import com.payroute.payment.dto.client.LedgerPostRequest;
import com.payroute.payment.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "ledger-service", configuration = FeignConfig.class)
public interface LedgerServiceClient {

    @PostMapping("/api/v1/ledger/entries")
    ApiResponse<LedgerEntryResponse> postEntry(@RequestBody LedgerPostRequest request);

    @PostMapping("/api/v1/ledger/entries/payment")
    ApiResponse<List<LedgerEntryResponse>> postPaymentEntries(
            @RequestParam("paymentId") Long paymentId,
            @RequestParam("debtorAccountId") Long debtorAccountId,
            @RequestParam("creditorAccountId") Long creditorAccountId,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam("currency") String currency,
            @RequestParam("rail") String rail,
            @RequestParam(value = "paymentMethod", required = false) String paymentMethod);

    @PostMapping("/api/v1/ledger/entries/{paymentId}/reverse")
    ApiResponse<List<LedgerEntryResponse>> reversePayment(@PathVariable("paymentId") Long paymentId);
}
