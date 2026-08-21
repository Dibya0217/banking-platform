package com.banking.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties("gateway")
@Getter
@Setter
public class GatewayProperties {

    private RateLimitProps rateLimit = new RateLimitProps();
    private Map<String, String> services = new HashMap<>();

    @Getter
    @Setter
    public static class RateLimitProps {
        private int requestsPerMinute = 100;
    }
}
