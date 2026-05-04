package com.payroute.payment.validation;

import com.payroute.payment.entity.PaymentOrder;
import com.payroute.payment.entity.ValidationResult;
import com.payroute.payment.entity.ValidationResultType;
import com.payroute.payment.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DuplicateChecker {

    private static final String RULE_NAME = "DUPLICATE_CHECK";
    private static final int DUPLICATE_WINDOW_MINUTES = 5;

    private final PaymentOrderRepository paymentOrderRepository;

    public ValidationResult check(PaymentOrder payment) {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(DUPLICATE_WINDOW_MINUTES);

        List<PaymentOrder> duplicates = paymentOrderRepository
                .findByDebtorAccountIdAndAmountAndCurrencyAndCreatedAtAfter(
                        payment.getDebtorAccountId(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        windowStart);

        // Exclude the current payment from results
        long duplicateCount = duplicates.stream()
                .filter(p -> !p.getId().equals(payment.getId()))
                .count();

        if (duplicateCount > 0) {
            return ValidationResult.builder()
                    .payment(payment)
                    .ruleName(RULE_NAME)
                    .result(ValidationResultType.FAIL)
                    .message("Potential duplicate payment detected. " + duplicateCount
                            + " similar payment(s) found in the last " + DUPLICATE_WINDOW_MINUTES + " minutes")
                    .checkedAt(LocalDateTime.now())
                    .build();
        }

        return ValidationResult.builder()
                .payment(payment)
                .ruleName(RULE_NAME)
                .result(ValidationResultType.PASS)
                .message("No duplicate payments detected")
                .checkedAt(LocalDateTime.now())
                .build();
    }
}
