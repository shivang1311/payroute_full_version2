package com.payroute.payment.validation;

import com.payroute.payment.entity.PaymentOrder;
import com.payroute.payment.entity.ValidationResult;
import com.payroute.payment.entity.ValidationResultType;
import com.payroute.payment.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class VelocityChecker {

    private static final String RULE_NAME = "VELOCITY_CHECK";
    private static final int MAX_TRANSACTIONS_PER_HOUR = 20;

    private final PaymentOrderRepository paymentOrderRepository;

    public ValidationResult check(PaymentOrder payment) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        long transactionCount = paymentOrderRepository
                .countByDebtorAccountIdAndCreatedAtAfter(payment.getDebtorAccountId(), oneHourAgo);

        if (transactionCount >= MAX_TRANSACTIONS_PER_HOUR) {
            return ValidationResult.builder()
                    .payment(payment)
                    .ruleName(RULE_NAME)
                    .result(ValidationResultType.FAIL)
                    .message("Velocity limit exceeded. " + transactionCount
                            + " transactions in the last hour (max: " + MAX_TRANSACTIONS_PER_HOUR + ")")
                    .checkedAt(LocalDateTime.now())
                    .build();
        }

        return ValidationResult.builder()
                .payment(payment)
                .ruleName(RULE_NAME)
                .result(ValidationResultType.PASS)
                .message("Velocity check passed. " + transactionCount
                        + " transactions in the last hour")
                .checkedAt(LocalDateTime.now())
                .build();
    }
}
