package com.banking.fraud.rule;

public interface FraudRule {
    FraudRuleResult evaluate(FraudCheckContext context);
}
