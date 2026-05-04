package com.payroute.payment.service;

import com.payroute.payment.entity.PaymentOrder;
import com.payroute.payment.entity.PaymentStatus;
import com.payroute.payment.exception.ResourceNotFoundException;
import com.payroute.payment.mapper.PaymentMapper;
import com.payroute.payment.repository.PaymentOrderRepository;
import org.junit.jupiter.api.DisplayName;
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

/**
 * Validates {@link PaymentService#retryPayment(Long, String)}:
 *  - resets FAILED / VALIDATION_FAILED → INITIATED
 *  - rejects retries on payments in any other status
 *  - rejects retries on payments that don't exist
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService.retryPayment")
class PaymentServiceRetryTest {

    @Mock PaymentOrderRepository paymentOrderRepository;
    @Mock PaymentMapper paymentMapper;
    @InjectMocks PaymentService paymentService;

    private PaymentOrder paymentWithStatus(PaymentStatus s) {
        return PaymentOrder.builder()
                .id(99L).reference("PR000099").status(s)
                .build();
    }

    @Test
    @DisplayName("FAILED → resets to INITIATED and persists")
    void resetsFailedPayment() {
        PaymentOrder p = paymentWithStatus(PaymentStatus.FAILED);
        when(paymentOrderRepository.findById(99L)).thenReturn(Optional.of(p));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentOrder result = paymentService.retryPayment(99L, "ops-user-1");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(result.getUpdatedBy()).isEqualTo("ops-user-1");
        verify(paymentOrderRepository).save(p);
    }

    @Test
    @DisplayName("VALIDATION_FAILED → resets to INITIATED")
    void resetsValidationFailedPayment() {
        PaymentOrder p = paymentWithStatus(PaymentStatus.VALIDATION_FAILED);
        when(paymentOrderRepository.findById(99L)).thenReturn(Optional.of(p));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentOrder result = paymentService.retryPayment(99L, "ops-user-1");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.INITIATED);
    }

    @Test
    @DisplayName("COMPLETED → IllegalStateException, no persistence")
    void rejectsCompletedPayment() {
        PaymentOrder p = paymentWithStatus(PaymentStatus.COMPLETED);
        when(paymentOrderRepository.findById(99L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> paymentService.retryPayment(99L, "ops"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be retried")
                .hasMessageContaining("COMPLETED");
        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("PROCESSING → IllegalStateException")
    void rejectsProcessingPayment() {
        PaymentOrder p = paymentWithStatus(PaymentStatus.PROCESSING);
        when(paymentOrderRepository.findById(99L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> paymentService.retryPayment(99L, "ops"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Missing payment → ResourceNotFoundException")
    void missingPaymentThrows() {
        when(paymentOrderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.retryPayment(404L, "ops"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
