package com.payroute.payment.service;

import com.payroute.payment.entity.PaymentOrder;
import com.payroute.payment.entity.PaymentStatus;
import com.payroute.payment.exception.ResourceNotFoundException;
import com.payroute.payment.mapper.PaymentMapper;
import com.payroute.payment.repository.PaymentOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService cancel + hold")
class PaymentServiceCancelHoldTest {

    @Mock PaymentOrderRepository paymentOrderRepository;
    @Mock PaymentMapper paymentMapper;
    @InjectMocks PaymentService paymentService;

    private PaymentOrder withStatus(PaymentStatus s) {
        return PaymentOrder.builder().id(50L).reference("PR000050").status(s).build();
    }

    @Nested
    @DisplayName("cancelPayment")
    class Cancel {
        @Test
        @DisplayName("INITIATED → FAILED")
        void cancelsInFlight() {
            PaymentOrder p = withStatus(PaymentStatus.INITIATED);
            when(paymentOrderRepository.findById(50L)).thenReturn(Optional.of(p));
            when(paymentOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PaymentOrder r = paymentService.cancelPayment(50L, "user request", "ops1");
            assertThat(r.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(r.getUpdatedBy()).isEqualTo("ops1");
        }

        @Test
        @DisplayName("PROCESSING → FAILED")
        void cancelsProcessing() {
            PaymentOrder p = withStatus(PaymentStatus.PROCESSING);
            when(paymentOrderRepository.findById(50L)).thenReturn(Optional.of(p));
            when(paymentOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PaymentOrder r = paymentService.cancelPayment(50L, "fraud", "ops1");
            assertThat(r.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("COMPLETED → IllegalStateException")
        void rejectsCompleted() {
            PaymentOrder p = withStatus(PaymentStatus.COMPLETED);
            when(paymentOrderRepository.findById(50L)).thenReturn(Optional.of(p));
            assertThatThrownBy(() -> paymentService.cancelPayment(50L, "x", "ops"))
                    .isInstanceOf(IllegalStateException.class);
            verify(paymentOrderRepository, never()).save(any());
        }

        @Test
        @DisplayName("FAILED already → IllegalStateException")
        void rejectsAlreadyFailed() {
            PaymentOrder p = withStatus(PaymentStatus.FAILED);
            when(paymentOrderRepository.findById(50L)).thenReturn(Optional.of(p));
            assertThatThrownBy(() -> paymentService.cancelPayment(50L, "x", "ops"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("missing payment → ResourceNotFoundException")
        void missing() {
            when(paymentOrderRepository.findById(404L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> paymentService.cancelPayment(404L, "x", "ops"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("holdPayment")
    class Hold {
        @Test
        @DisplayName("VALIDATED → HELD")
        void holdsActive() {
            PaymentOrder p = withStatus(PaymentStatus.VALIDATED);
            when(paymentOrderRepository.findById(50L)).thenReturn(Optional.of(p));
            when(paymentOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PaymentOrder r = paymentService.holdPayment(50L, "review", "ops1");
            assertThat(r.getStatus()).isEqualTo(PaymentStatus.HELD);
        }

        @Test
        @DisplayName("HELD already → IllegalStateException")
        void rejectsAlreadyHeld() {
            PaymentOrder p = withStatus(PaymentStatus.HELD);
            when(paymentOrderRepository.findById(50L)).thenReturn(Optional.of(p));
            assertThatThrownBy(() -> paymentService.holdPayment(50L, "x", "ops"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("COMPLETED → IllegalStateException")
        void rejectsCompleted() {
            PaymentOrder p = withStatus(PaymentStatus.COMPLETED);
            when(paymentOrderRepository.findById(50L)).thenReturn(Optional.of(p));
            assertThatThrownBy(() -> paymentService.holdPayment(50L, "x", "ops"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
