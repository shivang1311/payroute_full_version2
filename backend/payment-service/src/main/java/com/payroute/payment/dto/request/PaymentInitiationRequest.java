package com.payroute.payment.dto.request;

import com.payroute.payment.entity.InitiationChannel;
import com.payroute.payment.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentInitiationRequest {

    @NotNull(message = "Debtor account ID is required")
    private Long debtorAccountId;

    @NotNull(message = "Creditor account ID is required")
    private Long creditorAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    private String purposeCode;

    private InitiationChannel initiationChannel;

    /** UPI / BANK_TRANSFER / OTHER. Defaults to OTHER server-side if absent. */
    private PaymentMethod paymentMethod;

    /**
     * 4–6 digit transaction PIN. Required for CUSTOMER-initiated payments.
     * Verified via iam-service before the payment is persisted.
     */
    private String transactionPin;

    private String idempotencyKey;
}
