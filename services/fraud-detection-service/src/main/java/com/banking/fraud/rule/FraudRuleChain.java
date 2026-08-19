package com.banking.fraud.rule;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FraudRuleChain {

    private final BlacklistCheckRule blacklistCheckRule;
    private final VelocityCheckRule velocityCheckRule;
    private final LargeTransactionRule largeTransactionRule;

    /**
     * Evaluates all rules. Blacklist is checked first (short-circuit on CRITICAL).
     * Remaining rules always run to collect all violations.
     */
    public List<FraudRuleResult> evaluate(FraudCheckContext context) {
        List<FraudRuleResult> results = new ArrayList<>();

        FraudRuleResult blacklistResult = blacklistCheckRule.evaluate(context);
        results.add(blacklistResult);
        if (!blacklistResult.passed()) {
            return results;
        }

        results.add(velocityCheckRule.evaluate(context));
        results.add(largeTransactionRule.evaluate(context));

        return results;
    }

    public boolean hasFailures(List<FraudRuleResult> results) {
        return results.stream().anyMatch(r -> !r.passed());
    }
}
