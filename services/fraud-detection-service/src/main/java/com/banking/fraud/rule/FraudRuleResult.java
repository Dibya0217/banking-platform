package com.banking.fraud.rule;

import com.banking.fraud.entity.FraudAlertSeverity;

public record FraudRuleResult(
        boolean passed,
        String ruleName,
        String reason,
        FraudAlertSeverity severity
) {
    public static FraudRuleResult pass(String ruleName) {
        return new FraudRuleResult(true, ruleName, null, null);
    }

    public static FraudRuleResult fail(String ruleName, String reason, FraudAlertSeverity severity) {
        return new FraudRuleResult(false, ruleName, reason, severity);
    }
}
