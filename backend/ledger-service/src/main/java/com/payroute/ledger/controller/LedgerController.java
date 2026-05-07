package com.payroute.ledger.controller;

import com.payroute.ledger.dto.request.LedgerPostRequest;
import com.payroute.ledger.dto.response.*;
import com.payroute.ledger.entity.RailType;
import com.payroute.ledger.repository.LedgerEntryRepository;
import com.payroute.ledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * REST endpoints for the internal ledger: entry posting, payment-level
 * debit/credit/fee batches, reversals, account summary, statements (JSON + CSV),
 * GL export, and fee-income stats. Customer reads are gated by
 * {@code enforceAccountOwnership} — a CUSTOMER caller may only access entries
 * for accounts owned by their party (verified via party-service).
 */
@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Tag(name = "Ledger", description = "Ledger entry management APIs")
public class LedgerController {

    /** Role that triggers the ownership / read-restriction checks. */
    private static final String ROLE_CUSTOMER = "CUSTOMER";

    private final LedgerService ledgerService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final com.payroute.ledger.service.StatementService statementService;
    private final com.payroute.ledger.client.PartyServiceClient partyServiceClient;

    /**
     * §4.2 Ownership enforcement for ledger views: a CUSTOMER may only read entries
     * for accounts they own. ADMIN / OPERATIONS / RECONCILIATION / COMPLIANCE bypass.
     * Other callers without a role header (internal service-to-service) also bypass.
     */
    private void enforceAccountOwnership(Long accountId, String role, String partyIdHeader) {
        if (!ROLE_CUSTOMER.equalsIgnoreCase(role)) return;
        if (accountId == null) {
            throw new com.payroute.ledger.exception.ForbiddenException("Account id is required");
        }
        if (partyIdHeader == null || partyIdHeader.isBlank()) {
            throw new com.payroute.ledger.exception.ForbiddenException("Customer party context missing");
        }
        Long callerPartyId;
        try {
            callerPartyId = Long.parseLong(partyIdHeader);
        } catch (NumberFormatException nfe) {
            throw new com.payroute.ledger.exception.ForbiddenException("Invalid party context");
        }
        java.util.Map<String, Object> body;
        try {
            var resp = partyServiceClient.validateAccountById(accountId);
            body = resp == null ? null : resp.getBody();
        } catch (Exception ex) {
            throw new com.payroute.ledger.exception.ForbiddenException("Unable to verify account ownership");
        }
        if (body == null) {
            throw new com.payroute.ledger.exception.ForbiddenException("Account not found");
        }
        Object dataObj = body.get("data");
        if (!(dataObj instanceof java.util.Map<?, ?> data) || !Boolean.TRUE.equals(data.get("exists"))) {
            throw new com.payroute.ledger.exception.ForbiddenException("Account not found");
        }
        Object partyIdObj = data.get("partyId");
        if (partyIdObj == null) {
            throw new com.payroute.ledger.exception.ForbiddenException("You are not authorized to view this account");
        }
        Long ownerPartyId = Long.valueOf(partyIdObj.toString());
        if (!callerPartyId.equals(ownerPartyId)) {
            throw new com.payroute.ledger.exception.ForbiddenException(
                    "You are not authorized to view this account's ledger");
        }
    }

    @GetMapping("/statement")
    @Operation(summary = "Account statement", description = "Entries with running balance for an account within a date range")
    public ResponseEntity<ApiResponse<StatementResponse>> getStatement(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Party-Id", required = false) String partyId) {
        enforceAccountOwnership(accountId, role, partyId);
        StatementResponse response = statementService.generate(accountId, from, to);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping(value = "/statement.csv", produces = "text/csv")
    @Operation(summary = "Download account statement as CSV")
    public ResponseEntity<String> getStatementCsv(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Party-Id", required = false) String partyId) {
        enforceAccountOwnership(accountId, role, partyId);
        StatementResponse response = statementService.generate(accountId, from, to);
        String csv = statementService.toCsv(response);
        String filename = "statement-acct" + accountId + "-" + from + "-to-" + to + ".csv";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(csv);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    @Operation(summary = "GL export",
            description = "Export all ledger entries within a date range as CSV for external GL reconciliation (§9).")
    public ResponseEntity<String> exportLedgerCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (ROLE_CUSTOMER.equalsIgnoreCase(role)) {
            throw new com.payroute.ledger.exception.ForbiddenException(
                    "Customers are not authorized to export the full ledger");
        }
        java.util.List<com.payroute.ledger.entity.LedgerEntry> entries =
                ledgerEntryRepository.findByEntryDateBetween(from, to);
        StringBuilder sb = new StringBuilder();
        sb.append("entry_id,entry_date,payment_id,account_id,entry_type,amount,currency,balance_after,narrative,created_at,created_by\n");
        for (var e : entries) {
            sb.append(e.getId()).append(',')
              .append(e.getEntryDate()).append(',')
              .append(e.getPaymentId()).append(',')
              .append(e.getAccountId()).append(',')
              .append(e.getEntryType()).append(',')
              .append(e.getAmount()).append(',')
              .append(e.getCurrency()).append(',')
              .append(e.getBalanceAfter() == null ? "" : e.getBalanceAfter()).append(',')
              .append(csvEscape(e.getNarrative())).append(',')
              .append(e.getCreatedAt() == null ? "" : e.getCreatedAt()).append(',')
              .append(csvEscape(e.getCreatedBy()))
              .append('\n');
        }
        String filename = "ledger-export-" + from + "-to-" + to + ".csv";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(sb.toString());
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        boolean needsQuote = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String escaped = s.replace("\"", "\"\"");
        return needsQuote ? "\"" + escaped + "\"" : escaped;
    }

    @GetMapping("/stats/fee-income")
    @Operation(summary = "Fee income totals", description = "Total + daily fee income within a date range")
    public ResponseEntity<ApiResponse<FeeIncomeResponse>> getFeeIncome(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        java.math.BigDecimal total = ledgerEntryRepository.feeIncomeTotal(from, to);
        if (total == null) total = java.math.BigDecimal.ZERO;
        java.util.List<FeeIncomeResponse.DayPoint> byDay = new java.util.ArrayList<>();
        for (Object[] row : ledgerEntryRepository.feeIncomeByDay(from, to)) {
            LocalDate d;
            Object raw = row[0];
            if (raw instanceof LocalDate ld) d = ld;
            else if (raw instanceof java.sql.Date sd) d = sd.toLocalDate();
            else d = LocalDate.parse(raw.toString());
            java.math.BigDecimal amt = row[1] == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(row[1].toString());
            byDay.add(FeeIncomeResponse.DayPoint.builder().date(d).amount(amt).build());
        }
        return ResponseEntity.ok(ApiResponse.success(
                FeeIncomeResponse.builder().total(total).byDay(byDay).build()));
    }

    @GetMapping("/stats/fees-for-payments")
    @Operation(summary = "Total fees for a set of payment IDs",
            description = "Sum of FEE ledger entries for the given payment IDs. Used by settlement-service to compute batch fees.")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> feesForPayments(
            @RequestParam("ids") java.util.List<Long> ids) {
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        if (ids == null || ids.isEmpty()) {
            out.put("total", BigDecimal.ZERO);
            return ResponseEntity.ok(ApiResponse.success(out));
        }
        BigDecimal total = ledgerEntryRepository.feeTotalForPayments(ids);
        out.put("total", total == null ? BigDecimal.ZERO : total);
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    @GetMapping("/stats/payment-ids-on")
    @Operation(summary = "Debit-side payment IDs for a date",
            description = "Distinct payment IDs with a DEBIT entry posted on the given date. Used by reconciliation.")
    public ResponseEntity<ApiResponse<java.util.List<Long>>> paymentIdsOn(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(ledgerEntryRepository.findDebitPaymentIdsOnDate(date)));
    }

    @PostMapping("/entries")
    @Operation(summary = "Post a single ledger entry")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> postEntry(
            @Valid @RequestBody LedgerPostRequest request) {
        LedgerEntryResponse response = ledgerService.postEntry(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ledger entry created", response));
    }

    @GetMapping("/entries")
    @Operation(summary = "Get ledger entries with optional filters")
    public ResponseEntity<ApiResponse<PagedResponse<LedgerEntryResponse>>> getEntries(
            @RequestParam(required = false) Long paymentId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 20) Pageable pageable,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Party-Id", required = false) String partyId) {
        // Customers must target a specific account they own; unfiltered / other-account reads are blocked.
        if (ROLE_CUSTOMER.equalsIgnoreCase(role)) {
            if (accountId == null) {
                throw new com.payroute.ledger.exception.ForbiddenException(
                        "accountId is required for customer ledger queries");
            }
            enforceAccountOwnership(accountId, role, partyId);
        }
        PagedResponse<LedgerEntryResponse> response =
                ledgerService.getEntries(paymentId, accountId, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/entries/{id}")
    @Operation(summary = "Get a ledger entry by ID")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> getEntryById(@PathVariable Long id) {
        LedgerEntryResponse response = ledgerService.getEntryById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/entries/payment")
    @Operation(summary = "Post full payment entries (debit + credit + fee)")
    public ResponseEntity<ApiResponse<List<LedgerEntryResponse>>> postPaymentEntries(
            @RequestParam Long paymentId,
            @RequestParam Long debtorAccountId,
            @RequestParam Long creditorAccountId,
            @RequestParam BigDecimal amount,
            @RequestParam String currency,
            @RequestParam RailType rail,
            @RequestParam(required = false) String paymentMethod) {
        List<LedgerEntryResponse> responses = ledgerService.postPaymentEntries(
                paymentId, debtorAccountId, creditorAccountId, amount, currency, rail, paymentMethod);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment entries created", responses));
    }

    @PostMapping("/entries/{paymentId}/reverse")
    @Operation(summary = "Reverse all entries for a payment")
    public ResponseEntity<ApiResponse<List<LedgerEntryResponse>>> reversePayment(
            @PathVariable Long paymentId) {
        List<LedgerEntryResponse> responses = ledgerService.reversePayment(paymentId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment reversed", responses));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get account summary with aggregated debits, credits, fees, and net balance")
    public ResponseEntity<ApiResponse<AccountSummaryResponse>> getAccountSummary(
            @RequestParam Long accountId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Party-Id", required = false) String partyId) {
        enforceAccountOwnership(accountId, role, partyId);
        AccountSummaryResponse response = ledgerService.getAccountSummary(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
