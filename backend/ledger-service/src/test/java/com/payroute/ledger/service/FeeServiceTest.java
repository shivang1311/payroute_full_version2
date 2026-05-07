package com.payroute.ledger.service;

import com.payroute.ledger.dto.request.FeeScheduleRequest;
import com.payroute.ledger.dto.response.FeeScheduleResponse;
import com.payroute.ledger.entity.FeeSchedule;
import com.payroute.ledger.entity.FeeType;
import com.payroute.ledger.entity.RailType;
import com.payroute.ledger.exception.ResourceNotFoundException;
import com.payroute.ledger.mapper.FeeScheduleMapper;
import com.payroute.ledger.repository.FeeScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeeServiceTest {

    @Mock FeeScheduleRepository repo;
    @Mock FeeScheduleMapper mapper;
    @InjectMocks FeeService service;

    private FeeSchedule percentage(BigDecimal pct) {
        return FeeSchedule.builder()
                .id(1L).product("PAYMENT").rail(RailType.NEFT)
                .feeType(FeeType.PERCENTAGE).value(pct)
                .currency("INR").effectiveFrom(LocalDate.of(2026, 1, 1)).active(true).build();
    }

    private FeeSchedule flat(BigDecimal amount) {
        return FeeSchedule.builder()
                .id(2L).product("PAYMENT").rail(RailType.IMPS)
                .feeType(FeeType.FLAT).value(amount)
                .currency("INR").effectiveFrom(LocalDate.of(2026, 1, 1)).active(true).build();
    }

    @BeforeEach
    void setUp() {
        lenient().when(mapper.toResponse(any(FeeSchedule.class)))
                .thenAnswer(inv -> {
                    FeeSchedule fs = inv.getArgument(0);
                    return FeeScheduleResponse.builder()
                            .id(fs.getId()).product(fs.getProduct())
                            .rail(fs.getRail()).feeType(fs.getFeeType())
                            .value(fs.getValue()).build();
                });
        lenient().when(mapper.toResponseList(any())).thenAnswer(inv -> {
            List<FeeSchedule> list = inv.getArgument(0);
            return list.stream().map(mapper::toResponse).toList();
        });
    }

    // -------------------- computeFee --------------------

    @Nested
    @DisplayName("computeFee")
    class Compute {

        @Test
        @DisplayName("PERCENTAGE: 0.25% of 10000 → 25.00")
        void percentageRate() {
            when(repo.findActiveSchedule(any(), any(), any())).thenReturn(Optional.of(percentage(new BigDecimal("0.25"))));

            BigDecimal fee = service.computeFee("PAYMENT", RailType.NEFT,
                    new BigDecimal("10000"), LocalDate.now());
            assertThat(fee).isEqualByComparingTo("25.00");
        }

        @Test
        @DisplayName("FLAT: returns the configured value regardless of amount")
        void flatRate() {
            when(repo.findActiveSchedule(any(), any(), any())).thenReturn(Optional.of(flat(new BigDecimal("10.00"))));

            BigDecimal fee = service.computeFee("PAYMENT", RailType.IMPS,
                    new BigDecimal("99999"), LocalDate.now());
            assertThat(fee).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("PERCENTAGE with min bound applies floor")
        void percentageWithMinBound() {
            FeeSchedule fs = percentage(new BigDecimal("0.10"));
            fs.setMinFee(new BigDecimal("50.00"));
            when(repo.findActiveSchedule(any(), any(), any())).thenReturn(Optional.of(fs));

            // 0.1% of 1000 = 1.00, but min is 50 → 50.00
            BigDecimal fee = service.computeFee("PAYMENT", RailType.NEFT,
                    new BigDecimal("1000"), LocalDate.now());
            assertThat(fee).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("PERCENTAGE with max bound applies ceiling")
        void percentageWithMaxBound() {
            FeeSchedule fs = percentage(new BigDecimal("1.00"));
            fs.setMaxFee(new BigDecimal("100.00"));
            when(repo.findActiveSchedule(any(), any(), any())).thenReturn(Optional.of(fs));

            // 1% of 1000000 = 10000 — capped at 100
            BigDecimal fee = service.computeFee("PAYMENT", RailType.NEFT,
                    new BigDecimal("1000000"), LocalDate.now());
            assertThat(fee).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("rounds half-up to 2 decimals")
        void roundingHalfUp() {
            // 0.25% of 10001 = 25.0025 → 25.00
            when(repo.findActiveSchedule(any(), any(), any())).thenReturn(Optional.of(percentage(new BigDecimal("0.25"))));
            BigDecimal fee = service.computeFee("PAYMENT", RailType.NEFT,
                    new BigDecimal("10001"), LocalDate.now());
            assertThat(fee).isEqualByComparingTo("25.00");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException if no active schedule matches")
        void noScheduleFound() {
            when(repo.findActiveSchedule(any(), any(), any())).thenReturn(Optional.empty());
            // Extracted out of the lambda to satisfy S5778.
            BigDecimal amount = new BigDecimal("1000");
            LocalDate today = LocalDate.now();
            assertThatThrownBy(() -> service.computeFee("PAYMENT", RailType.WIRE, amount, today))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // -------------------- CRUD --------------------

    @Nested
    @DisplayName("CRUD")
    class Crud {
        @Test
        void createSchedule_setsActiveTrue() {
            FeeScheduleRequest req = FeeScheduleRequest.builder()
                    .product("PAYMENT").rail(RailType.NEFT).feeType(FeeType.PERCENTAGE)
                    .value(new BigDecimal("0.25")).currency("INR")
                    .effectiveFrom(LocalDate.of(2026, 1, 1)).build();

            FeeSchedule entity = percentage(new BigDecimal("0.25"));
            when(mapper.toEntity(req)).thenReturn(entity);
            when(repo.save(any(FeeSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

            service.createFeeSchedule(req);

            assertThat(entity.getActive()).isTrue();
            verify(repo).save(entity);
        }

        @Test
        void getById_found() {
            FeeSchedule fs = percentage(new BigDecimal("0.25"));
            when(repo.findById(1L)).thenReturn(Optional.of(fs));
            assertThat(service.getFeeScheduleById(1L)).isNotNull();
        }

        @Test
        void getById_notFound() {
            when(repo.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getFeeScheduleById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getActiveFeeSchedules() {
            when(repo.findByActiveTrue()).thenReturn(List.of(percentage(new BigDecimal("0.25"))));
            assertThat(service.getActiveFeeSchedules()).hasSize(1);
        }

        @Test
        void update() {
            FeeScheduleRequest req = FeeScheduleRequest.builder()
                    .product("PAYMENT").rail(RailType.NEFT).feeType(FeeType.PERCENTAGE)
                    .value(new BigDecimal("0.30")).currency("INR")
                    .effectiveFrom(LocalDate.now()).build();
            FeeSchedule fs = percentage(new BigDecimal("0.25"));
            when(repo.findById(1L)).thenReturn(Optional.of(fs));
            when(repo.save(fs)).thenReturn(fs);

            service.updateFeeSchedule(1L, req);
            verify(mapper).updateEntity(req, fs);
        }

        @Test
        void deactivate() {
            FeeSchedule fs = percentage(new BigDecimal("0.25"));
            when(repo.findById(1L)).thenReturn(Optional.of(fs));

            service.deactivateFeeSchedule(1L);

            assertThat(fs.getActive()).isFalse();
            verify(repo).save(fs);
        }
    }
}
