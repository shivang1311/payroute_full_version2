package com.payroute.payment.validation;

import com.payroute.payment.entity.PaymentOrder;
import com.payroute.payment.entity.ValidationResult;
import com.payroute.payment.entity.ValidationResultType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class FieldValidator {

    private static final Set<String> VALID_CURRENCIES = Set.of(
            "USD", "EUR", "GBP", "INR", "JPY", "AUD", "CAD", "CHF", "CNY", "SGD");

    private static final String RULE_NAME = "FIELD_VALIDATION";

    public List<ValidationResult> validate(PaymentOrder payment) {
        List<ValidationResult> results = new ArrayList<>();

        if (payment.getDebtorAccountId() == null) {
            results.add(buildResult(payment, ValidationResultType.FAIL, "Debtor account ID is required"));
        }

        if (payment.getCreditorAccountId() == null) {
            results.add(buildResult(payment, ValidationResultType.FAIL, "Creditor account ID is required"));
        }

        if (payment.getDebtorAccountId() != null && payment.getCreditorAccountId() != null
                && payment.getDebtorAccountId().equals(payment.getCreditorAccountId())) {
            results.add(buildResult(payment, ValidationResultType.FAIL,
                    "Debtor and creditor accounts must be different"));
        }

        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            results.add(buildResult(payment, ValidationResultType.FAIL, "Amount must be greater than zero"));
        }

        if (payment.getCurrency() == null || payment.getCurrency().isBlank()) {
            results.add(buildResult(payment, ValidationResultType.FAIL, "Currency is required"));
        } else if (!VALID_CURRENCIES.contains(payment.getCurrency().toUpperCase())) {
            results.add(buildResult(payment, ValidationResultType.FAIL,
                    "Unsupported currency: " + payment.getCurrency()));
        }

        if (results.isEmpty()) {
            results.add(buildResult(payment, ValidationResultType.PASS, "All field validations passed"));
        }

        return results;
    }

    private ValidationResult buildResult(PaymentOrder payment, ValidationResultType type, String message) {
        return ValidationResult.builder()
                .payment(payment)
                .ruleName(RULE_NAME)
                .result(type)
                .message(message)
                .checkedAt(LocalDateTime.now())
                .build();
    }
}
