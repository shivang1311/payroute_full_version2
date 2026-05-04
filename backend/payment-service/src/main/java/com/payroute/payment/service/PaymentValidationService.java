package com.payroute.payment.service;

import com.payroute.payment.entity.PaymentOrder;
import com.payroute.payment.entity.ValidationResult;
import com.payroute.payment.entity.ValidationResultType;
import com.payroute.payment.repository.ValidationResultRepository;
import com.payroute.payment.validation.DuplicateChecker;
import com.payroute.payment.validation.FieldValidator;
import com.payroute.payment.validation.LimitChecker;
import com.payroute.payment.validation.VelocityChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentValidationService {

    private final FieldValidator fieldValidator;
    private final DuplicateChecker duplicateChecker;
    private final VelocityChecker velocityChecker;
    private final LimitChecker limitChecker;
    private final ValidationResultRepository validationResultRepository;

    /**
     * Runs all four validators against the payment order.
     * Each validation result is persisted.
     *
     * @param payment the payment order to validate
     * @return true if all validations passed, false otherwise
     */
    @Transactional
    public boolean validate(PaymentOrder payment) {
        log.info("Starting validation for payment id={}", payment.getId());

        List<ValidationResult> allResults = new ArrayList<>();

        // 1. Field validation
        List<ValidationResult> fieldResults = fieldValidator.validate(payment);
        allResults.addAll(fieldResults);

        // 2. Duplicate check
        ValidationResult duplicateResult = duplicateChecker.check(payment);
        allResults.add(duplicateResult);

        // 3. Velocity check
        ValidationResult velocityResult = velocityChecker.check(payment);
        allResults.add(velocityResult);

        // 4. Limit check
        ValidationResult limitResult = limitChecker.check(payment);
        allResults.add(limitResult);

        // Persist all results
        validationResultRepository.saveAll(allResults);

        boolean allPassed = allResults.stream()
                .allMatch(r -> r.getResult() == ValidationResultType.PASS);

        log.info("Validation completed for payment id={}, result={}", payment.getId(),
                allPassed ? "PASS" : "FAIL");

        return allPassed;
    }
}
