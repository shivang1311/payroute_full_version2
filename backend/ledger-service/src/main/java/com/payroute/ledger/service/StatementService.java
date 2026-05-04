package com.payroute.ledger.service;

import com.payroute.ledger.dto.response.StatementResponse;
import com.payroute.ledger.entity.EntryType;
import com.payroute.ledger.entity.LedgerEntry;
import com.payroute.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StatementService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public StatementResponse generate(Long accountId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to dates are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to date must be on/after from date");
        }

        BigDecimal opening = ledgerEntryRepository.netBalanceBefore(accountId, from);
        if (opening == null) opening = BigDecimal.ZERO;

        List<LedgerEntry> rows = ledgerEntryRepository.findStatementEntries(accountId, from, to);

        BigDecimal running = opening;
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalDebits = BigDecimal.ZERO;
        String currency = null;
        List<StatementResponse.StatementLine> lines = new ArrayList<>(rows.size());

        for (LedgerEntry e : rows) {
            if (currency == null) currency = e.getCurrency();
            BigDecimal debit = BigDecimal.ZERO;
            BigDecimal credit = BigDecimal.ZERO;
            switch (e.getEntryType()) {
                case CREDIT, REVERSAL -> {
                    credit = e.getAmount();
                    running = running.add(credit);
                    totalCredits = totalCredits.add(credit);
                }
                case DEBIT, FEE, TAX -> {
                    debit = e.getAmount();
                    running = running.subtract(debit);
                    totalDebits = totalDebits.add(debit);
                }
            }
            lines.add(StatementResponse.StatementLine.builder()
                    .entryId(e.getId())
                    .paymentId(e.getPaymentId())
                    .entryDate(e.getEntryDate())
                    .postedAt(e.getCreatedAt())
                    .entryType(e.getEntryType())
                    .debit(debit)
                    .credit(credit)
                    .currency(e.getCurrency())
                    .narrative(e.getNarrative())
                    .runningBalance(running)
                    .build());
        }

        return StatementResponse.builder()
                .accountId(accountId)
                .currency(currency)
                .fromDate(from)
                .toDate(to)
                .openingBalance(opening)
                .closingBalance(running)
                .totalCredits(totalCredits)
                .totalDebits(totalDebits)
                .entryCount(lines.size())
                .entries(lines)
                .build();
    }

    /**
     * Render the statement as CSV for download.
     */
    public String toCsv(StatementResponse statement) {
        StringBuilder sb = new StringBuilder();
        sb.append("Account Statement\n");
        sb.append("Account ID,").append(statement.getAccountId()).append('\n');
        sb.append("Currency,").append(nullSafe(statement.getCurrency())).append('\n');
        sb.append("Period,").append(statement.getFromDate()).append(" to ").append(statement.getToDate()).append('\n');
        sb.append("Opening Balance,").append(statement.getOpeningBalance()).append('\n');
        sb.append("Closing Balance,").append(statement.getClosingBalance()).append('\n');
        sb.append("Total Credits,").append(statement.getTotalCredits()).append('\n');
        sb.append("Total Debits,").append(statement.getTotalDebits()).append('\n');
        sb.append('\n');
        sb.append("Entry ID,Payment ID,Date,Posted At,Type,Debit,Credit,Currency,Narrative,Running Balance\n");
        if (statement.getEntries() != null) {
            for (StatementResponse.StatementLine l : statement.getEntries()) {
                sb.append(l.getEntryId()).append(',')
                        .append(l.getPaymentId() == null ? "" : l.getPaymentId()).append(',')
                        .append(l.getEntryDate()).append(',')
                        .append(l.getPostedAt() == null ? "" : l.getPostedAt()).append(',')
                        .append(l.getEntryType()).append(',')
                        .append(l.getDebit() == null ? "" : l.getDebit()).append(',')
                        .append(l.getCredit() == null ? "" : l.getCredit()).append(',')
                        .append(nullSafe(l.getCurrency())).append(',')
                        .append(csvEscape(l.getNarrative())).append(',')
                        .append(l.getRunningBalance())
                        .append('\n');
            }
        }
        return sb.toString();
    }

    private String nullSafe(String s) { return s == null ? "" : s; }

    private String csvEscape(String s) {
        if (s == null) return "";
        boolean needsQuote = s.contains(",") || s.contains("\"") || s.contains("\n");
        String escaped = s.replace("\"", "\"\"");
        return needsQuote ? "\"" + escaped + "\"" : escaped;
    }
}
