package com.payroute.ledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroute.ledger.dto.request.LedgerPostRequest;
import com.payroute.ledger.dto.response.AccountSummaryResponse;
import com.payroute.ledger.dto.response.LedgerEntryResponse;
import com.payroute.ledger.dto.response.StatementResponse;
import com.payroute.ledger.entity.EntryType;
import com.payroute.ledger.entity.LedgerEntry;
import com.payroute.ledger.entity.RailType;
import com.payroute.ledger.exception.ResourceNotFoundException;
import com.payroute.ledger.service.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    // -------------------- /statement (enforceAccountOwnership branches) --------------------

    /** Build the validateAccountById response shape expected by enforceAccountOwnership. */
    private static ResponseEntity<Map<String, Object>> ownershipResponse(Long ownerPartyId, boolean exists) {
        Map<String, Object> data = new HashMap<>();
        data.put("exists", exists);
        if (ownerPartyId != null) data.put("partyId", ownerPartyId);
        Map<String, Object> body = new HashMap<>();
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    private static StatementResponse stubStatement() {
        return StatementResponse.builder()
                .accountId(30L).currency("INR")
                .fromDate(LocalDate.of(2026, 5, 1)).toDate(LocalDate.of(2026, 5, 5))
                .openingBalance(BigDecimal.ZERO).closingBalance(BigDecimal.ZERO)
                .totalCredits(BigDecimal.ZERO).totalDebits(BigDecimal.ZERO)
                .entryCount(0).entries(List.of()).build();
    }

    @Test
    void getStatement_admin_bypassesOwnership() throws Exception {
        when(statementService.generate(eq(30L), any(), any())).thenReturn(stubStatement());
        mvc.perform(get("/api/v1/ledger/statement")
                        .param("accountId", "30")
                        .param("from", "2026-05-01").param("to", "2026-05-05")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(30));
    }

    @Test
    void getStatement_customer_ownerMatches_succeeds() throws Exception {
        when(partyServiceClient.validateAccountById(30L)).thenReturn(ownershipResponse(7L, true));
        when(statementService.generate(eq(30L), any(), any())).thenReturn(stubStatement());
        mvc.perform(get("/api/v1/ledger/statement")
                        .param("accountId", "30")
                        .param("from", "2026-05-01").param("to", "2026-05-05")
                        .header("X-User-Role", "CUSTOMER")
                        .header("X-Party-Id", "7"))
                .andExpect(status().isOk());
    }

    @Test
    void getStatement_customer_ownerMismatch_forbidden() throws Exception {
        when(partyServiceClient.validateAccountById(30L)).thenReturn(ownershipResponse(99L, true));
        mvc.perform(get("/api/v1/ledger/statement")
                        .param("accountId", "30")
                        .param("from", "2026-05-01").param("to", "2026-05-05")
                        .header("X-User-Role", "CUSTOMER")
                        .header("X-Party-Id", "7"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStatement_customer_missingPartyHeader_forbidden() throws Exception {
        mvc.perform(get("/api/v1/ledger/statement")
                        .param("accountId", "30")
                        .param("from", "2026-05-01").param("to", "2026-05-05")
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStatement_customer_invalidPartyHeader_forbidden() throws Exception {
        mvc.perform(get("/api/v1/ledger/statement")
                        .param("accountId", "30")
                        .param("from", "2026-05-01").param("to", "2026-05-05")
                        .header("X-User-Role", "CUSTOMER")
                        .header("X-Party-Id", "not-a-number"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStatement_customer_partyServiceThrows_forbidden() throws Exception {
        when(partyServiceClient.validateAccountById(30L))
                .thenThrow(new RuntimeException("connection refused"));
        mvc.perform(get("/api/v1/ledger/statement")
                        .param("accountId", "30")
                        .param("from", "2026-05-01").param("to", "2026-05-05")
                        .header("X-User-Role", "CUSTOMER")
                        .header("X-Party-Id", "7"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStatement_customer_accountNotFound_forbidden() throws Exception {
        when(partyServiceClient.validateAccountById(30L)).thenReturn(ownershipResponse(null, false));
        mvc.perform(get("/api/v1/ledger/statement")
                        .param("accountId", "30")
                        .param("from", "2026-05-01").param("to", "2026-05-05")
                        .header("X-User-Role", "CUSTOMER")
                        .header("X-Party-Id", "7"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStatement_customer_partyIdMissingInResponse_forbidden() throws Exception {
        when(partyServiceClient.validateAccountById(30L)).thenReturn(ownershipResponse(null, true));
        mvc.perform(get("/api/v1/ledger/statement")
                        .param("accountId", "30")
                        .param("from", "2026-05-01").param("to", "2026-05-05")
                        .header("X-User-Role", "CUSTOMER")
                        .header("X-Party-Id", "7"))
                .andExpect(status().isForbidden());
    }

    // -------------------- /statement.csv --------------------

    @Test
    void getStatementCsv_returnsCsvAttachment() throws Exception {
        when(statementService.generate(eq(30L), any(), any())).thenReturn(stubStatement());
        when(statementService.toCsv(any())).thenReturn("Account Statement\nAccount ID,30\n");
        mvc.perform(get("/api/v1/ledger/statement.csv")
                        .param("accountId", "30")
                        .param("from", "2026-05-01").param("to", "2026-05-05")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("statement-acct30-2026-05-01-to-2026-05-05.csv")));
    }

    // -------------------- /export.csv --------------------

    @Test
    void exportLedgerCsv_customer_forbidden() throws Exception {
        mvc.perform(get("/api/v1/ledger/export.csv")
                        .param("from", "2026-05-01").param("to", "2026-05-05")
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportLedgerCsv_admin_returnsRows() throws Exception {
        LedgerEntry e = LedgerEntry.builder()
                .id(1L).paymentId(10L).accountId(100L)
                .entryType(EntryType.DEBIT).amount(new BigDecimal("100"))
                .currency("INR").narrative("Payment, with comma")
                .balanceAfter(new BigDecimal("900"))
                .entryDate(LocalDate.of(2026, 5, 1))
                .createdAt(LocalDateTime.of(2026, 5, 1, 10, 0))
                .createdBy("ops").build();
        LedgerEntry e2 = LedgerEntry.builder()
                .id(2L).paymentId(11L).accountId(100L)
                .entryType(EntryType.CREDIT).amount(new BigDecimal("100"))
                .currency("INR").narrative(null)        // null narrative
                .balanceAfter(null).entryDate(LocalDate.of(2026, 5, 2))
                .createdAt(null).createdBy(null).build();
        when(ledgerEntryRepository.findByEntryDateBetween(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
                .thenReturn(List.of(e, e2));

        mvc.perform(get("/api/v1/ledger/export.csv")
                        .param("from", "2026-05-01").param("to", "2026-05-05")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("entry_id,entry_date")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"Payment, with comma\"")));
    }

    // -------------------- /stats/fee-income --------------------

    @Test
    void feeIncome_total_andByDay() throws Exception {
        when(ledgerEntryRepository.feeIncomeTotal(any(), any())).thenReturn(new BigDecimal("250"));
        // Mix of date types — controller normalizes LocalDate / java.sql.Date / String
        when(ledgerEntryRepository.feeIncomeByDay(any(), any())).thenReturn(List.of(
                new Object[]{ LocalDate.of(2026, 5, 1), new BigDecimal("100") },
                new Object[]{ java.sql.Date.valueOf("2026-05-02"), 50 },
                new Object[]{ "2026-05-03", null }
        ));
        mvc.perform(get("/api/v1/ledger/stats/fee-income")
                        .param("from", "2026-05-01").param("to", "2026-05-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(250))
                .andExpect(jsonPath("$.data.byDay.length()").value(3));
    }

    @Test
    void feeIncome_nullTotal_returnsZero() throws Exception {
        when(ledgerEntryRepository.feeIncomeTotal(any(), any())).thenReturn(null);
        when(ledgerEntryRepository.feeIncomeByDay(any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/v1/ledger/stats/fee-income")
                        .param("from", "2026-05-01").param("to", "2026-05-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // -------------------- /stats/fees-for-payments --------------------

    @Test
    void feesForPayments_normal() throws Exception {
        when(ledgerEntryRepository.feeTotalForPayments(List.of(1L, 2L, 3L)))
                .thenReturn(new BigDecimal("75"));
        mvc.perform(get("/api/v1/ledger/stats/fees-for-payments")
                        .param("ids", "1", "2", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(75));
    }

    @Test
    void feesForPayments_nullTotal_returnsZero() throws Exception {
        when(ledgerEntryRepository.feeTotalForPayments(any())).thenReturn(null);
        mvc.perform(get("/api/v1/ledger/stats/fees-for-payments")
                        .param("ids", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // -------------------- /stats/payment-ids-on --------------------

    @Test
    void paymentIdsOn_returnsList() throws Exception {
        when(ledgerEntryRepository.findDebitPaymentIdsOnDate(LocalDate.of(2026, 5, 5)))
                .thenReturn(List.of(11L, 12L));
        mvc.perform(get("/api/v1/ledger/stats/payment-ids-on").param("date", "2026-05-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value(11));
    }

    // -------------------- /summary --------------------

    @Test
    void getAccountSummary_admin_succeeds() throws Exception {
        when(ledgerService.getAccountSummary(30L)).thenReturn(
                AccountSummaryResponse.builder()
                        .accountId(30L).totalDebits(BigDecimal.ZERO).totalCredits(BigDecimal.ZERO)
                        .totalFees(BigDecimal.ZERO).netBalance(BigDecimal.ZERO).build());
        mvc.perform(get("/api/v1/ledger/summary")
                        .param("accountId", "30")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(30));
    }

    // -------------------- /entries (customer ownership branches) --------------------

    @Test
    void getEntries_customer_missingAccountId_forbidden() throws Exception {
        mvc.perform(get("/api/v1/ledger/entries").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getEntries_customer_withOwnedAccount_succeeds() throws Exception {
        when(partyServiceClient.validateAccountById(30L)).thenReturn(ownershipResponse(7L, true));
        when(ledgerService.getEntries(any(), eq(30L), any(), any(), any()))
                .thenReturn(com.payroute.ledger.dto.response.PagedResponse.<LedgerEntryResponse>builder()
                        .content(List.of()).page(0).size(20)
                        .totalElements(0).totalPages(0).last(true).build());
        mvc.perform(get("/api/v1/ledger/entries")
                        .param("accountId", "30")
                        .header("X-User-Role", "CUSTOMER")
                        .header("X-Party-Id", "7"))
                .andExpect(status().isOk());
    }
}
