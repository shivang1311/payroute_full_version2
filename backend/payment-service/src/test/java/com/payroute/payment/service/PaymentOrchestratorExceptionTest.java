package com.payroute.payment.service;

import com.payroute.payment.client.*;
import com.payroute.payment.dto.client.*;
import com.payroute.payment.dto.response.ApiResponse;
import com.payroute.payment.entity.PaymentOrder;
import com.payroute.payment.entity.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies the new behaviour: whenever a payment fails, the orchestrator
 * fires {@code exceptionServiceClient.createException(...)} with sensible
 * category/priority/description so the row appears in the Exception Queue.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentOrchestrator → exception-service auto-raise")
class PaymentOrchestratorExceptionTest {

    @Mock PaymentService paymentService;
    @Mock PaymentValidationService paymentValidationService;
    @Mock PartyServiceClient partyServiceClient;
    @Mock ComplianceServiceClient complianceServiceClient;
    @Mock RoutingServiceClient routingServiceClient;
    @Mock LedgerServiceClient ledgerServiceClient;
    @Mock NotificationServiceClient notificationServiceClient;
    @Mock ExceptionServiceClient exceptionServiceClient;

    @InjectMocks PaymentOrchestrator orchestrator;

    private PaymentOrder payment;

    @BeforeEach
    void setUp() {
        payment = PaymentOrder.builder()
                .id(42L)
                .reference("PR000042")
                .amount(new BigDecimal("1000.00"))
                .currency("INR")
                .debtorAccountId(1L)
                .creditorAccountId(2L)
                .status(PaymentStatus.INITIATED)
                .build();

        // getEntityById is called inside publishWebhookSafe (after each failure path)
        lenient().when(paymentService.getEntityById(42L)).thenReturn(payment);
    }

    @Test
    @DisplayName("Internal validation failure → raises VALIDATION/MEDIUM exception")
    void validationFailureRaisesException() {
        // Account validation passes
        AccountValidationResponse ok = new AccountValidationResponse();
        ok.setExists(true);
        ok.setActive(true);
        when(partyServiceClient.validateAccountById(any()))
                .thenReturn(ApiResponse.success(ok));
        // Internal validation fails
        when(paymentValidationService.validate(payment)).thenReturn(false);

        orchestrator.orchestratePayment(payment);

        ArgumentCaptor<ExceptionCaseRequest> captor = ArgumentCaptor.forClass(ExceptionCaseRequest.class);
        verify(exceptionServiceClient, times(1)).createException(captor.capture());
        ExceptionCaseRequest req = captor.getValue();
        assertThat(req.getPaymentId()).isEqualTo(42L);
        assertThat(req.getCategory()).isEqualTo("VALIDATION");
        assertThat(req.getPriority()).isEqualTo("MEDIUM");
        assertThat(req.getDescription()).contains("PR000042").contains("validation");
    }

    @Test
    @DisplayName("Account validation throws → raises VALIDATION/HIGH exception")
    void accountValidationFailureRaisesException() {
        // Debtor account does NOT exist
        AccountValidationResponse missing = new AccountValidationResponse();
        missing.setExists(false);
        missing.setActive(false);
        when(partyServiceClient.validateAccountById(any()))
                .thenReturn(ApiResponse.success(missing));

        orchestrator.orchestratePayment(payment);

        ArgumentCaptor<ExceptionCaseRequest> captor = ArgumentCaptor.forClass(ExceptionCaseRequest.class);
        verify(exceptionServiceClient, times(1)).createException(captor.capture());
        ExceptionCaseRequest req = captor.getValue();
        assertThat(req.getCategory()).isEqualTo("VALIDATION");
        assertThat(req.getPriority()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("onPaymentFailed callback → raises RAIL/HIGH exception")
    void railFailureRaisesException() {
        orchestrator.onPaymentFailed(42L, "Beneficiary bank declined the transfer");

        ArgumentCaptor<ExceptionCaseRequest> captor = ArgumentCaptor.forClass(ExceptionCaseRequest.class);
        verify(exceptionServiceClient, times(1)).createException(captor.capture());
        ExceptionCaseRequest req = captor.getValue();
        assertThat(req.getCategory()).isEqualTo("RAIL");
        assertThat(req.getPriority()).isEqualTo("HIGH");
        assertThat(req.getDescription()).contains("Beneficiary bank declined");
    }

    @Test
    @DisplayName("exception-service down → orchestrator does NOT propagate the failure")
    void exceptionServiceOutageIsSwallowed() {
        // Simulate exception-service throwing
        doThrow(new RuntimeException("exception-service down"))
                .when(exceptionServiceClient).createException(any());

        // Should not throw — best-effort
        orchestrator.onPaymentFailed(42L, "Some rail error");

        verify(exceptionServiceClient, times(1)).createException(any());
        // Status update + notification still happened
        verify(paymentService, times(1)).updateStatus(42L, PaymentStatus.FAILED, "SYSTEM");
    }
}
