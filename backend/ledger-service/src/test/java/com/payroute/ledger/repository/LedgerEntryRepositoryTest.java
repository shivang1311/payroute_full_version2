package com.payroute.ledger.repository;

import com.payroute.ledger.entity.EntryType;
import com.payroute.ledger.entity.LedgerEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LedgerEntryRepositoryTest {

    @Autowired LedgerEntryRepository repo;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void seed() {
        repo.deleteAll();

        // Account 100 - debit / credit pair on payment 1
        repo.save(LedgerEntry.builder()
                .paymentId(1L).accountId(100L).entryType(EntryType.DEBIT)
                .amount(new BigDecimal("1000")).currency("INR")
                .entryDate(today).createdBy("test").build());
        repo.save(LedgerEntry.builder()
                .paymentId(1L).accountId(200L).entryType(EntryType.CREDIT)
                .amount(new BigDecimal("1000")).currency("INR")
                .entryDate(today).createdBy("test").build());
        repo.save(LedgerEntry.builder()
                .paymentId(1L).accountId(100L).entryType(EntryType.FEE)
                .amount(new BigDecimal("25")).currency("INR")
                .entryDate(today).createdBy("test").build());

        // Account 100 - history (yesterday)
        repo.save(LedgerEntry.builder()
                .paymentId(2L).accountId(100L).entryType(EntryType.CREDIT)
                .amount(new BigDecimal("500")).currency("INR")
                .entryDate(today.minusDays(1)).createdBy("test").build());
        repo.save(LedgerEntry.builder()
                .paymentId(2L).accountId(100L).entryType(EntryType.DEBIT)
                .amount(new BigDecimal("100")).currency("INR")
                .entryDate(today.minusDays(1)).createdBy("test").build());
    }

    @Nested
    @DisplayName("findByPaymentId")
    class FindByPayment {
        @Test
        void returnsAllEntriesForPayment() {
            List<LedgerEntry> entries = repo.findByPaymentId(1L);
            assertThat(entries).hasSize(3);
            assertThat(entries).extracting(LedgerEntry::getEntryType)
                    .containsExactlyInAnyOrder(EntryType.DEBIT, EntryType.CREDIT, EntryType.FEE);
        }

        @Test
        void emptyForUnknownPayment() {
            assertThat(repo.findByPaymentId(999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByAccountId")
    class FindByAccount {
        @Test
        void allHistoryForAccount() {
            List<LedgerEntry> entries = repo.findByAccountId(100L);
            assertThat(entries).hasSize(4); // debit/fee today + credit/debit yesterday
        }
    }

    @Nested
    @DisplayName("findStatementEntries (date-range, ordered)")
    class StatementEntries {
        @Test
        void returnsTodayOnly() {
            List<LedgerEntry> entries = repo.findStatementEntries(100L, today, today);
            assertThat(entries).hasSize(2); // DEBIT + FEE for acct 100 today
        }

        @Test
        void returnsFullRange() {
            List<LedgerEntry> entries = repo.findStatementEntries(100L, today.minusDays(1), today);
            assertThat(entries).hasSize(4);
        }

        @Test
        void otherAccountReturnsEmpty() {
            assertThat(repo.findStatementEntries(999L, today.minusYears(1), today)).isEmpty();
        }
    }

    @Nested
    @DisplayName("netBalanceBefore (opening balance)")
    class NetBalance {
        @Test
        void aggregatesDebitsCreditsFees() {
            // Yesterday: +500 (credit) -100 (debit) = +400 opening for "today"
            BigDecimal balance = repo.netBalanceBefore(100L, today);
            assertThat(balance).isEqualByComparingTo("400");
        }

        @Test
        void zeroWhenNothingBeforeDate() {
            BigDecimal balance = repo.netBalanceBefore(100L, today.minusYears(5));
            assertThat(balance).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("feeIncomeTotal / feeTotalForPayments")
    class FeeAggregates {
        @Test
        void totalFeeIncomeToday() {
            BigDecimal total = repo.feeIncomeTotal(today, today);
            assertThat(total).isEqualByComparingTo("25");
        }

        @Test
        void totalFeeIncomeForUnknownRangeIsZero() {
            BigDecimal total = repo.feeIncomeTotal(today.plusYears(1), today.plusYears(1));
            assertThat(total).isEqualByComparingTo("0");
        }

        @Test
        void feeTotalForSpecificPayments() {
            BigDecimal total = repo.feeTotalForPayments(List.of(1L));
            assertThat(total).isEqualByComparingTo("25");
        }

        @Test
        void feeTotalForPaymentsWithoutFeeIsZero() {
            BigDecimal total = repo.feeTotalForPayments(List.of(2L));
            assertThat(total).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("findDebitPaymentIdsOnDate (reconciliation)")
    class DebitPaymentIds {
        @Test
        void returnsTodaysDebitPayments() {
            List<Long> ids = repo.findDebitPaymentIdsOnDate(today);
            assertThat(ids).containsExactly(1L);
        }

        @Test
        void emptyOnFutureDate() {
            assertThat(repo.findDebitPaymentIdsOnDate(today.plusDays(1))).isEmpty();
        }
    }

    @Nested
    @DisplayName("paged queries")
    class PagedQueries {
        @Test
        void findByPaymentIdPaged() {
            assertThat(repo.findByPaymentId(1L, PageRequest.of(0, 10)).getContent()).hasSize(3);
        }

        @Test
        void findByAccountIdPaged() {
            assertThat(repo.findByAccountId(100L, PageRequest.of(0, 10)).getContent()).hasSize(4);
        }

        @Test
        void findByEntryDateBetween() {
            assertThat(repo.findByEntryDateBetween(today, today)).hasSize(3);
        }
    }
}
