package com.payroute.ledger.service;

import com.payroute.ledger.dto.request.LedgerPostRequest;
import com.payroute.ledger.dto.response.LedgerEntryResponse;
import com.payroute.ledger.entity.EntryType;
import com.payroute.ledger.entity.LedgerEntry;
import com.payroute.ledger.entity.RailType;
import com.payroute.ledger.exception.ResourceNotFoundException;
import com.payroute.ledger.mapper.LedgerEntryMapper;
import com.payroute.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock LedgerEntryRepository repo;
    @Mock LedgerEntryMapper mapper;
    @Mock FeeService feeService;
    @InjectMocks LedgerService service;

    @BeforeEach
    void setUp() {
        lenient().when(mapper.toResponse(any(LedgerEntry.class)))
                .thenAnswer(inv -> {
                    LedgerEntry e = inv.getArgument(0);
                    return LedgerEntryResponse.builder()
                            .id(e.getId()).paymentId(e.getPaymentId())
                            .accountId(e.getAccountId()).entryType(e.getEntryType())
                            .amount(e.getAmount()).currency(e.getCurrency())
                            .narrative(e.getNarrative()).build();
                });
        lenient().when(mapper.toResponseList(any())).thenAnswer(inv -> {
            List<LedgerEntry> list = inv.getArgument(0);
            return list.stream().map(mapper::toResponse).toList();
        });
    }

    // -------------------- postEntry --------------------

    @Nested
    @DisplayName("postEntry")
    class PostEntry {
        @Test
        void savesAndReturnsResponse() {
            LedgerEntry entity = LedgerEntry.builder()
                    .id(1L).paymentId(10L).accountId(100L)
                    .entryType(EntryType.DEBIT).amount(new BigDecimal("100"))
                    .currency("INR").build();
            when(mapper.toEntity(any(LedgerPostRequest.class))).thenReturn(entity);
            when(repo.save(any(LedgerEntry.class))).thenReturn(entity);

            LedgerPostRequest req = LedgerPostRequest.builder()
                    .paymentId(10L).accountId(100L).entryType(EntryType.DEBIT)
                    .amount(new BigDecimal("100")).currency("INR").build();

            LedgerEntryResponse resp = service.postEntry(req);

            assertThat(resp).isNotNull();
            assertThat(entity.getEntryDate()).isNotNull(); // service sets entryDate=today
            assertThat(entity.getCreatedBy()).isEqualTo("SYSTEM");
        }
    }

    // -------------------- postPaymentEntries --------------------

    @Nested
    @DisplayName("postPaymentEntries (debit + credit + fee)")
    class PostPaymentEntries {
        @Test
        @DisplayName("posts 3 entries (debit, credit, fee) for non-UPI payment")
        void debitCreditFee() {
            when(repo.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));
            when(feeService.computeFee(any(), any(), any(), any())).thenReturn(new BigDecimal("25"));

            service.postPaymentEntries(10L, 100L, 200L, new BigDecimal("10000"), "INR", RailType.NEFT, null);

            ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
            verify(repo, times(3)).save(captor.capture());

            List<EntryType> types = captor.getAllValues().stream().map(LedgerEntry::getEntryType).toList();
            assertThat(types).containsExactly(EntryType.DEBIT, EntryType.CREDIT, EntryType.FEE);
        }

        @Test
        @DisplayName("UPI: skips fee leg entirely (only 2 entries)")
        void upiNoFee() {
            when(repo.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            service.postPaymentEntries(10L, 100L, 200L, new BigDecimal("5000"), "INR", RailType.IMPS, "UPI");

            ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
            verify(repo, times(2)).save(captor.capture());
            verify(feeService, never()).computeFee(any(), any(), any(), any());

            assertThat(captor.getAllValues()).extracting(LedgerEntry::getEntryType)
                    .containsExactly(EntryType.DEBIT, EntryType.CREDIT);
        }

        @Test
        @DisplayName("zero fee from FeeService → no FEE row written")
        void zeroFeeOmitsFeeEntry() {
            when(repo.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));
            when(feeService.computeFee(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

            service.postPaymentEntries(10L, 100L, 200L, new BigDecimal("1000"), "INR", RailType.BOOK, null);

            verify(repo, times(2)).save(any(LedgerEntry.class));
        }

        @Test
        @DisplayName("missing fee schedule is logged but doesn't fail the payment")
        void missingFeeSchedule() {
            when(repo.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));
            when(feeService.computeFee(any(), any(), any(), any()))
                    .thenThrow(new ResourceNotFoundException("FeeSchedule", "rail", "WIRE"));

            // Should not throw — fee leg is best-effort.
            service.postPaymentEntries(10L, 100L, 200L, new BigDecimal("1000"), "INR", RailType.WIRE, null);
            verify(repo, times(2)).save(any(LedgerEntry.class));
        }

        @Test
        @DisplayName("debit + credit narratives reference the paymentId")
        void narrativeSetsPaymentId() {
            when(repo.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));
            when(feeService.computeFee(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

            service.postPaymentEntries(42L, 100L, 200L, new BigDecimal("1000"), "INR", RailType.BOOK, null);

            ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
            verify(repo, times(2)).save(captor.capture());
            assertThat(captor.getAllValues().get(0).getNarrative()).contains("42");
        }
    }

    // -------------------- reversePayment --------------------

    @Nested
    @DisplayName("reversePayment")
    class Reverse {
        @Test
        void createsMirrorReversalEntries() {
            LedgerEntry debit = LedgerEntry.builder().id(1L).paymentId(10L)
                    .accountId(100L).entryType(EntryType.DEBIT)
                    .amount(new BigDecimal("100")).currency("INR").build();
            LedgerEntry credit = LedgerEntry.builder().id(2L).paymentId(10L)
                    .accountId(200L).entryType(EntryType.CREDIT)
                    .amount(new BigDecimal("100")).currency("INR").build();
            when(repo.findByPaymentId(10L)).thenReturn(List.of(debit, credit));
            when(repo.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            service.reversePayment(10L);

            ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
            verify(repo, times(2)).save(captor.capture());
            assertThat(captor.getAllValues()).extracting(LedgerEntry::getEntryType)
                    .containsOnly(EntryType.REVERSAL);
        }

        @Test
        void throwsWhenNoEntriesForPayment() {
            when(repo.findByPaymentId(99L)).thenReturn(Collections.emptyList());
            assertThatThrownBy(() -> service.reversePayment(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void rejectsAlreadyReversed() {
            LedgerEntry rev = LedgerEntry.builder().id(1L).paymentId(10L)
                    .entryType(EntryType.REVERSAL).build();
            when(repo.findByPaymentId(10L)).thenReturn(List.of(rev));
            assertThatThrownBy(() -> service.reversePayment(10L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been reversed");
        }
    }

    // -------------------- getEntriesByPayment / getEntryById --------------------

    @Nested
    @DisplayName("read queries")
    class Reads {
        @Test
        void getEntriesByPayment_returnsAll() {
            LedgerEntry e = LedgerEntry.builder().id(1L).paymentId(10L)
                    .entryType(EntryType.DEBIT).amount(new BigDecimal("100"))
                    .currency("INR").build();
            when(repo.findByPaymentId(10L)).thenReturn(List.of(e));
            assertThat(service.getEntriesByPayment(10L)).hasSize(1);
        }

        @Test
        void getEntriesByPayment_throwsWhenEmpty() {
            when(repo.findByPaymentId(99L)).thenReturn(Collections.emptyList());
            assertThatThrownBy(() -> service.getEntriesByPayment(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getEntryById_found() {
            LedgerEntry e = LedgerEntry.builder().id(1L).build();
            when(repo.findById(1L)).thenReturn(java.util.Optional.of(e));
            assertThat(service.getEntryById(1L)).isNotNull();
        }

        @Test
        void getEntryById_notFound() {
            when(repo.findById(99L)).thenReturn(java.util.Optional.empty());
            assertThatThrownBy(() -> service.getEntryById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // -------------------- getEntries (filter routing) --------------------

    @Nested
    @DisplayName("getEntries filter routing")
    class FilterRouting {
        @Test
        void byPaymentAndAccount_callsCombinedQuery() {
            org.springframework.data.domain.Page<LedgerEntry> empty =
                    org.springframework.data.domain.Page.empty();
            when(repo.findByPaymentIdAndAccountId(any(), any(), any())).thenReturn(empty);
            service.getEntries(10L, 100L, null, null, org.springframework.data.domain.PageRequest.of(0, 10));
            verify(repo).findByPaymentIdAndAccountId(any(), any(), any());
        }

        @Test
        void byDateRange_callsDateRangeQuery() {
            org.springframework.data.domain.Page<LedgerEntry> empty = org.springframework.data.domain.Page.empty();
            when(repo.findByEntryDateBetween(any(LocalDate.class), any(LocalDate.class),
                    any(org.springframework.data.domain.Pageable.class))).thenReturn(empty);

            service.getEntries(null, null, LocalDate.now().minusDays(1), LocalDate.now(),
                    org.springframework.data.domain.PageRequest.of(0, 10));
            verify(repo).findByEntryDateBetween(any(LocalDate.class), any(LocalDate.class),
                    any(org.springframework.data.domain.Pageable.class));
        }

        @Test
        void noFilters_callsFindAll() {
            org.springframework.data.domain.Page<LedgerEntry> empty = org.springframework.data.domain.Page.empty();
            when(repo.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(empty);

            service.getEntries(null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 10));
            verify(repo).findAll(any(org.springframework.data.domain.Pageable.class));
        }
    }
}
