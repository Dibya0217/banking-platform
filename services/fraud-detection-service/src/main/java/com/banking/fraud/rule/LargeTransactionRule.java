package com.banking.fraud.rule;

import com.banking.fraud.entity.FraudAlertSeverity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class LargeTransactionRule implements FraudRule {

    static final String RULE_NAME = "LARGE_TRANSACTION";

    @Value("${fraud.large-transaction.threshold:500000}")
    private BigDecimal threshold;

    @Override
    public FraudRuleResult evaluate(FraudCheckContext context) {
        if (context.amount().compareTo(threshold) > 0) {
            return FraudRuleResult.fail(RULE_NAME,
                    "Transaction amount ₹" + context.amount() + " exceeds threshold ₹" + threshold,
                    FraudAlertSeverity.HIGH);
        }
        return FraudRuleResult.pass(RULE_NAME);
    }
}
