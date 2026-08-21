package com.banking.gateway.filter;

import com.banking.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private GatewayProperties gatewayProperties;
    private RateLimitFilter filter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        gatewayProperties = new GatewayProperties();
        gatewayProperties.getRateLimit().setRequestsPerMinute(100);

        filter = new RateLimitFilter(redisTemplate, gatewayProperties);

        chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void filter_belowLimit_allowsRequest() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(50L));
        // count != 1, so no expire call needed

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/accounts/123")
                .header("X-User-Id", "user-1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"))
                .isEqualTo("50");
    }

    @Test
    void filter_atLimit_allows() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(100L));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/accounts/123")
                .header("X-User-Id", "user-2")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"))
                .isEqualTo("0");
    }

    @Test
    void filter_exceedsLimit_returns429() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(101L));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/accounts/123")
                .header("X-User-Id", "user-3")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"))
                .isEqualTo("0");
    }
}
