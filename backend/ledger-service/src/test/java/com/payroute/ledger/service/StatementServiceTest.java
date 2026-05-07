package com.payroute.ledger.service;

import com.payroute.ledger.dto.response.StatementResponse;
import com.payroute.ledger.entity.EntryType;
import com.payroute.ledger.entity.LedgerEntry;
import com.payroute.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * StatementService is pure aggregation logic over the ledger — no IO except the
 * two repository reads. We mock those and assert the running-balance math,
 * sign rules per EntryType, and CSV escaping.
 */
@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock LedgerEntryRepository repo;
    @InjectMocks StatementService service;

    private static LedgerEntry entry(Long id, EntryType type, String amount, String narrative) {
        return LedgerEntry.builder()
                .id(id)
                .paymentId(id)
                .accountId(30L)
                .entryType(type)
                .amount(new BigDecimal(amount))
                .currency("INR")
                .narrative(narrative)
                .entryDate(LocalDate.of(2026, 5, 1))
                .createdAt(LocalDateTime.of(2026, 5, 1, 10, 0))
                .build();
    }

    @Nested
    @DisplayName("generate()")
    class Generate {

        @Test
        @DisplayName("rejects null from/to dates")
        void rejectsNullDates() {
            // Extracted out of the lambda to satisfy S5778 (only one possibly-throwing
            // call inside `assertThatThrownBy`).
            LocalDate today = LocalDate.now();
            assertThatThrownBy(() -> service.generate(30L, null, today))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("from and to dates");
            assertThatThrownBy(() -> service.generate(30L, today, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects to-date earlier than from-date")
        void rejectsInvertedRange() {
            LocalDate from = LocalDate.of(2026, 5, 5);
            LocalDate to = LocalDate.of(2026, 5, 1);
            assertThatThrownBy(() -> service.generate(30L, from, to))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("on/after");
        }

        @Test
        @DisplayName("uses ZERO opening balance when repository returns null")
        void nullOpeningTreatedAsZero() {
            when(repo.netBalanceBefore(30L, LocalDate.of(2026, 5, 1))).thenReturn(null);
            when(repo.findStatementEntries(30L, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
                    .thenReturn(Collections.emptyList());

            StatementResponse out = service.generate(30L,
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5));

            assertThat(out.getOpeningBalance()).isEqualByComparingTo("0");
            assertThat(out.getClosingBalance()).isEqualByComparingTo("0");
            assertThat(out.getEntryCount()).isZero();
            assertThat(out.getEntries()).isEmpty();
        }

        @Test
        @DisplayName("CREDIT and REVERSAL increase running balance; DEBIT/FEE/TAX decrease it")
        void runningBalanceMath() {
            // Opening 1000. Apply +500 CREDIT, -200 DEBIT, +50 REVERSAL, -10 FEE, -2 TAX → 1338
            when(repo.netBalanceBefore(30L, LocalDate.of(2026, 5, 1)))
                    .thenReturn(new BigDecimal("1000"));
            when(repo.findStatementEntries(30L, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
                    .thenReturn(List.of(
                            entry(1L, EntryType.CREDIT,   "500", "credit"),
                            entry(2L, EntryType.DEBIT,    "200", "debit"),
                            entry(3L, EntryType.REVERSAL,  "50", "reversal"),
                            entry(4L, EntryType.FEE,       "10", "fee"),
                            entry(5L, EntryType.TAX,        "2", "tax")
                    ));

            StatementResponse out = service.generate(30L,
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5));

            assertThat(out.getOpeningBalance()).isEqualByComparingTo("1000");
            assertThat(out.getClosingBalance()).isEqualByComparingTo("1338");
            assertThat(out.getTotalCredits()).isEqualByComparingTo("550"); // 500 + 50
            assertThat(out.getTotalDebits()).isEqualByComparingTo("212");  // 200 + 10 + 2
            assertThat(out.getEntryCount()).isEqualTo(5);
            assertThat(out.getCurrency()).isEqualTo("INR");

            // running balance after each line
            assertThat(out.getEntries().get(0).getRunningBalance()).isEqualByComparingTo("1500");
            assertThat(out.getEntries().get(1).getRunningBalance()).isEqualByComparingTo("1300");
            assertThat(out.getEntries().get(2).getRunningBalance()).isEqualByComparingTo("1350");
            assertThat(out.getEntries().get(3).getRunningBalance()).isEqualByComparingTo("1340");
            assertThat(out.getEntries().get(4).getRunningBalance()).isEqualByComparingTo("1338");

            // debit/credit columns are populated correctly
            assertThat(out.getEntries().get(0).getCredit()).isEqualByComparingTo("500");
            assertThat(out.getEntries().get(0).getDebit()).isEqualByComparingTo("0");
            assertThat(out.getEntries().get(1).getDebit()).isEqualByComparingTo("200");
            assertThat(out.getEntries().get(1).getCredit()).isEqualByComparingTo("0");
        }

    }

    @Nested
    @DisplayName("toCsv()")
    class ToCsv {

        @Test
        @DisplayName("renders header rows with metadata")
        void rendersMetadata() {
            StatementResponse stmt = StatementResponse.builder()
                    .accountId(30L)
                    .currency("INR")
                    .fromDate(LocalDate.of(2026, 5, 1))
                    .toDate(LocalDate.of(2026, 5, 5))
                    .openingBalance(new BigDecimal("1000"))
                    .closingBalance(new BigDecimal("1500"))
                    .totalCredits(new BigDecimal("500"))
                    .totalDebits(BigDecimal.ZERO)
                    .entryCount(0)
                    .entries(Collections.emptyList())
                    .build();

            String csv = service.toCsv(stmt);

            // Chain assertions per S5853 — single subject, all expectations together.
            assertThat(csv)
                    .contains("Account ID,30")
                    .contains("Currency,INR")
                    .contains("Period,2026-05-01 to 2026-05-05")
                    .contains("Opening Balance,1000")
                    .contains("Closing Balance,1500")
                    .contains("Total Credits,500")
                    .contains("Total Debits,0");
        }

        @Test
        @DisplayName("renders blank for null currency / null entries list")
        void handlesNulls() {
            StatementResponse stmt = StatementResponse.builder()
                    .accountId(30L)
                    .currency(null)
                    .fromDate(LocalDate.of(2026, 5, 1))
                    .toDate(LocalDate.of(2026, 5, 5))
                    .openingBalance(BigDecimal.ZERO)
                    .closingBalance(BigDecimal.ZERO)
                    .totalCredits(BigDecimal.ZERO)
                    .totalDebits(BigDecimal.ZERO)
                    .entryCount(0)
                    .entries(null)
                    .build();

            String csv = service.toCsv(stmt);

            // null entries list is a valid empty body case — must not throw,
            // and the standard header row should still be emitted.
            assertThat(csv)
                    .contains("Currency,\n")
                    .contains("Entry ID,Payment ID");
        }

        @Test
        @DisplayName("escapes commas, quotes, and newlines in narrative")
        void escapesSpecialChars() {
            StatementResponse.StatementLine commaLine = StatementResponse.StatementLine.builder()
                    .entryId(1L).paymentId(1L)
                    .entryDate(LocalDate.of(2026, 5, 1))
                    .postedAt(LocalDateTime.of(2026, 5, 1, 10, 0))
                    .entryType(EntryType.CREDIT)
                    .debit(BigDecimal.ZERO).credit(new BigDecimal("100"))
                    .currency("INR")
                    .narrative("Salary, May")     // contains a comma → must be quoted
                    .runningBalance(new BigDecimal("100"))
                    .build();

            StatementResponse.StatementLine quoteLine = StatementResponse.StatementLine.builder()
                    .entryId(2L).paymentId(null)
                    .entryDate(LocalDate.of(2026, 5, 2))
                    .postedAt(null)               // null postedAt
                    .entryType(EntryType.DEBIT)
                    .debit(new BigDecimal("50")).credit(BigDecimal.ZERO)
                    .currency("INR")
                    .narrative("ref \"R-1\"")     // contains a quote → escape + quote
                    .runningBalance(new BigDecimal("50"))
                    .build();

            StatementResponse.StatementLine nlLine = StatementResponse.StatementLine.builder()
                    .entryId(3L).paymentId(3L)
                    .entryDate(LocalDate.of(2026, 5, 3))
                    .postedAt(LocalDateTime.of(2026, 5, 3, 9, 0))
                    .entryType(EntryType.FEE)
                    .debit(null).credit(null)     // null amounts → blank
                    .currency(null)               // null currency on a line → blank
                    .narrative("line\nbreak")     // contains newline → must be quoted
                    .runningBalance(new BigDecimal("0"))
                    .build();

            StatementResponse stmt = StatementResponse.builder()
                    .accountId(30L).currency("INR")
                    .fromDate(LocalDate.of(2026, 5, 1)).toDate(LocalDate.of(2026, 5, 5))
                    .openingBalance(BigDecimal.ZERO).closingBalance(BigDecimal.ZERO)
                    .totalCredits(BigDecimal.ZERO).totalDebits(BigDecimal.ZERO)
                    .entryCount(3)
                    .entries(List.of(commaLine, quoteLine, nlLine))
                    .build();

            String csv = service.toCsv(stmt);

            assertThat(csv)
                    // Commas in fields force enclosing quotes
                    .contains("\"Salary, May\"")
                    // Existing quotes are doubled per RFC-4180
                    .contains("\"ref \"\"R-1\"\"\"")
                    // Newlines trigger quoting too
                    .contains("\"line\nbreak\"")
                    // null payment id, postedAt, debit, credit, currency become empty fields
                    .contains("2,,2026-05-02,,DEBIT,50,0,INR,");
        }
    }
}
