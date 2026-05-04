package com.payroute.payment.validation;

import com.payroute.payment.entity.PaymentMethod;
import com.payroute.payment.entity.PaymentOrder;
import com.payroute.payment.entity.ValidationResult;
import com.payroute.payment.entity.ValidationResultType;
import com.payroute.payment.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class LimitChecker {

    private static final String RULE_NAME = "LIMIT_CHECK";

    /** Hard ceiling for any single payment, regardless of method. */
    private static final BigDecimal PER_TRANSACTION_LIMIT = new BigDecimal("5000000");

    /** UPI: max ₹1,00,000 per single transaction. */
    private static final BigDecimal UPI_TXN_LIMIT = new BigDecimal("100000");

    /** UPI: max ₹1,00,000 cumulative per debtor account per calendar day. */
    private static final BigDecimal UPI_DAILY_LIMIT = new BigDecimal("100000");

    /** Bank transfer: max ₹5,00,000 per single transaction. */
    private static final BigDecimal BANK_TRANSFER_TXN_LIMIT = new BigDecimal("500000");

    private final PaymentOrderRepository paymentOrderRepository;

    public ValidationResult check(PaymentOrder payment) {
        // 1. Per-transaction ceiling
        if (payment.getAmount().compareTo(PER_TRANSACTION_LIMIT) > 0) {
            return fail(payment, "Amount " + payment.getAmount()
                    + " exceeds per-transaction limit of " + PER_TRANSACTION_LIMIT);
        }

        // 2. UPI per-transaction cap (₹1,00,000)
        if (payment.getPaymentMethod() == PaymentMethod.UPI
                && payment.getAmount().compareTo(UPI_TXN_LIMIT) > 0) {
            return fail(payment, "Amount " + payment.getAmount()
                    + " exceeds the UPI per-transaction limit of " + UPI_TXN_LIMIT
                    + ". Use Bank Transfer for larger payments.");
        }

        // 3. Bank-transfer per-transaction cap (₹5,00,000)
        if (payment.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                && payment.getAmount().compareTo(BANK_TRANSFER_TXN_LIMIT) > 0) {
            return fail(payment, "Amount " + payment.getAmount()
                    + " exceeds the Bank Transfer per-transaction limit of " + BANK_TRANSFER_TXN_LIMIT + ".");
        }

        // 4. UPI daily limit (₹1,00,000 per debtor account per day)
        if (payment.getPaymentMethod() == PaymentMethod.UPI && payment.getDebtorAccountId() != null) {
            LocalDate today = LocalDate.now();
            LocalDateTime dayStart = today.atStartOfDay();
            LocalDateTime dayEnd = today.atTime(LocalTime.MAX);

            BigDecimal alreadyUsed = paymentOrderRepository.sumByDebtorAndMethodInWindow(
                    payment.getDebtorAccountId(), PaymentMethod.UPI, dayStart, dayEnd);
            if (alreadyUsed == null) alreadyUsed = BigDecimal.ZERO;

            BigDecimal projected = alreadyUsed.add(payment.getAmount());
            if (projected.compareTo(UPI_DAILY_LIMIT) > 0) {
                BigDecimal remaining = UPI_DAILY_LIMIT.subtract(alreadyUsed).max(BigDecimal.ZERO);
                return fail(payment,
                        "UPI daily limit of ₹" + UPI_DAILY_LIMIT
                                + " per account would be exceeded. Already used today: ₹"
                                + alreadyUsed + ". Remaining today: ₹" + remaining + ".");
            }
        }

        return ValidationResult.builder()
                .payment(payment)
                .ruleName(RULE_NAME)
                .result(ValidationResultType.PASS)
                .message("All limit checks passed")
                .checkedAt(LocalDateTime.now())
                .build();
    }

    private ValidationResult fail(PaymentOrder payment, String message) {
        return ValidationResult.builder()
                .payment(payment)
                .ruleName(RULE_NAME)
                .result(ValidationResultType.FAIL)
                .message(message)
                .checkedAt(LocalDateTime.now())
                .build();
    }
}
