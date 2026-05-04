package com.payroute.routing.engine;

import com.payroute.routing.dto.request.RouteRequest;
import com.payroute.routing.entity.RailType;
import com.payroute.routing.entity.RoutingRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RailSelector {

    private static final BigDecimal RTGS_THRESHOLD = new BigDecimal("200000");

    private final RuleEvaluator ruleEvaluator;

    /**
     * Evaluates the list of active routing rules (ordered by priority) against the payment data.
     * Returns the preferred rail of the first matching rule.
     * Falls back to NEFT for amounts less than 200000, RTGS otherwise.
     */
    public RailType selectRail(List<RoutingRule> activeRules, RouteRequest request) {
        for (RoutingRule rule : activeRules) {
            try {
                if (ruleEvaluator.evaluate(rule.getConditionJson(), request)) {
                    log.info("Rule matched: [id={}, name={}] -> rail={}", rule.getId(), rule.getName(),
                            rule.getPreferredRail());
                    return rule.getPreferredRail();
                }
            } catch (Exception e) {
                log.error("Error evaluating rule [id={}, name={}]: {}", rule.getId(), rule.getName(),
                        e.getMessage());
            }
        }

        RailType fallback = determineFallbackRail(request.getAmount());
        log.info("No rule matched for paymentId={}. Using fallback rail: {}", request.getPaymentId(), fallback);
        return fallback;
    }

    private RailType determineFallbackRail(BigDecimal amount) {
        if (amount != null && amount.compareTo(RTGS_THRESHOLD) >= 0) {
            return RailType.RTGS;
        }
        return RailType.NEFT;
    }
}
