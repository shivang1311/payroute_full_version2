package com.payroute.payment.dto.response;

import com.payroute.payment.entity.InitiationChannel;
import com.payroute.payment.entity.PaymentMethod;
import com.payroute.payment.entity.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private Long id;
    private String reference;
    private String idempotencyKey;
    private Long debtorAccountId;
    private Long creditorAccountId;
    private BigDecimal amount;
    private String currency;
    private String purposeCode;
    private InitiationChannel initiationChannel;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String initiatedBy;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private List<ValidationResultResponse> validationResults;
}
