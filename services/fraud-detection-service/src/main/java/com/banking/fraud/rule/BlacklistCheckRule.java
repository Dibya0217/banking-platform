package com.banking.fraud.rule;

import com.banking.fraud.entity.FraudAlertSeverity;
import com.banking.fraud.service.BlacklistCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BlacklistCheckRule implements FraudRule {

    static final String RULE_NAME = "BLACKLIST_CHECK";

    private final BlacklistCacheService blacklistCacheService;

    @Override
    public FraudRuleResult evaluate(FraudCheckContext context) {
        if (blacklistCacheService.isBlacklisted(context.fromAccountId())) {
            return FraudRuleResult.fail(RULE_NAME,
                    "Account " + context.fromAccountId() + " is blacklisted",
                    FraudAlertSeverity.CRITICAL);
        }
        return FraudRuleResult.pass(RULE_NAME);
    }
}
