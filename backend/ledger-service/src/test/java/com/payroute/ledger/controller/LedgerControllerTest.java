package com.payroute.ledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroute.ledger.dto.request.LedgerPostRequest;
import com.payroute.ledger.dto.response.LedgerEntryResponse;
import com.payroute.ledger.entity.EntryType;
import com.payroute.ledger.entity.RailType;
import com.payroute.ledger.exception.ResourceNotFoundException;
import com.payroute.ledger.service.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean LedgerService ledgerService;
    @MockBean com.payroute.ledger.repository.LedgerEntryRepository ledgerEntryRepository;
    @MockBean com.payroute.ledger.service.StatementService statementService;
    @MockBean com.payroute.ledger.client.PartyServiceClient partyServiceClient;

    private LedgerEntryResponse stub() {
        return LedgerEntryResponse.builder()
                .id(1L).paymentId(10L).accountId(100L)
                .entryType(EntryType.DEBIT).amount(new BigDecimal("100"))
                .currency("INR").narrative("Payment debit").build();
    }

    @Test
    void postEntry_returns201() throws Exception {
        when(ledgerService.postEntry(any())).thenReturn(stub());
        LedgerPostRequest req = LedgerPostRequest.builder()
                .paymentId(10L).accountId(100L).entryType(EntryType.DEBIT)
                .amount(new BigDecimal("100")).currency("INR").build();

        mvc.perform(post("/api/v1/ledger/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void postPaymentEntries_returns201() throws Exception {
        when(ledgerService.postPaymentEntries(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(stub()));

        mvc.perform(post("/api/v1/ledger/entries/payment")
                        .param("paymentId", "10").param("debtorAccountId", "100")
                        .param("creditorAccountId", "200").param("amount", "1000")
                        .param("currency", "INR").param("rail", "NEFT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void postPaymentEntries_withUpiMethod() throws Exception {
        when(ledgerService.postPaymentEntries(any(), any(), any(), any(), any(), any(), eq("UPI")))
                .thenReturn(List.of(stub(), stub()));

        mvc.perform(post("/api/v1/ledger/entries/payment")
                        .param("paymentId", "10").param("debtorAccountId", "100")
                        .param("creditorAccountId", "200").param("amount", "1000")
                        .param("currency", "INR").param("rail", "IMPS")
                        .param("paymentMethod", "UPI"))
                .andExpect(status().isCreated());
    }

    @Test
    void getEntries_byPaymentId() throws Exception {
        org.springframework.data.domain.Page<LedgerEntryResponse> dummy = org.springframework.data.domain.Page.empty();
        // Service returns PagedResponse, not Page — match what the controller expects
        when(ledgerService.getEntries(eq(10L), any(), any(), any(), any()))
                .thenReturn(com.payroute.ledger.dto.response.PagedResponse.<LedgerEntryResponse>builder()
                        .content(List.of(stub())).page(0).size(20)
                        .totalElements(1).totalPages(1).last(true).build());
        mvc.perform(get("/api/v1/ledger/entries").param("paymentId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].paymentId").value(10));
    }

    @Test
    void getEntryById_found() throws Exception {
        when(ledgerService.getEntryById(1L)).thenReturn(stub());
        mvc.perform(get("/api/v1/ledger/entries/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getEntryById_notFound() throws Exception {
        when(ledgerService.getEntryById(99L))
                .thenThrow(new ResourceNotFoundException("LedgerEntry", "id", 99L));
        mvc.perform(get("/api/v1/ledger/entries/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reversePayment_returns201() throws Exception {
        when(ledgerService.reversePayment(10L)).thenReturn(List.of(stub()));
        mvc.perform(post("/api/v1/ledger/entries/10/reverse"))
                .andExpect(status().isCreated());
    }

    @Test
    void reversePayment_alreadyReversed_returns409() throws Exception {
        // ledger-service GlobalExceptionHandler maps IllegalStateException to 409 Conflict
        when(ledgerService.reversePayment(10L))
                .thenThrow(new IllegalStateException("Payment 10 has already been reversed"));
        mvc.perform(post("/api/v1/ledger/entries/10/reverse"))
                .andExpect(status().is4xxClientError());
    }
}
