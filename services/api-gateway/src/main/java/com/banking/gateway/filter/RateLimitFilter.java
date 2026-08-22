package com.banking.gateway.filter;

import com.banking.gateway.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class RateLimitFilter implements WebFilter {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final GatewayProperties gatewayProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

        // No rate limit applied to unauthenticated requests — JWT filter handles rejection
        if (userId == null) {
            return chain.filter(exchange);
        }

        String key = "gateway:rate:" + userId;
        int limit = gatewayProperties.getRateLimit().getRequestsPerMinute();

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        // First request in this sliding window — set 60 s expiry
                        return redisTemplate.expire(key, Duration.ofMinutes(1))
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    if (count > limit) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders()
                                .add("X-RateLimit-Limit", String.valueOf(limit));
                        exchange.getResponse().getHeaders()
                                .add("X-RateLimit-Remaining", "0");
                        return exchange.getResponse().setComplete();
                    }
                    long remaining = Math.max(0, limit - count);
                    exchange.getResponse().getHeaders()
                            .add("X-RateLimit-Limit", String.valueOf(limit));
                    exchange.getResponse().getHeaders()
                            .add("X-RateLimit-Remaining", String.valueOf(remaining));
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> {
                    // On Redis failure, allow the request through (fail-open)
                    log.warn("Rate limit Redis error, allowing request: {}", e.getMessage());
                    return chain.filter(exchange);
                });
    }
}
