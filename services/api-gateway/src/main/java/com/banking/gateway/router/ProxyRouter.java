package com.banking.gateway.router;

import com.banking.gateway.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ProxyRouter {

    private final WebClient webClient;
    private final GatewayProperties gatewayProperties;

    @Bean
    public RouterFunction<ServerResponse> routerFunction() {
        return RouterFunctions.route()
                // Auth service
                .path("/api/v1/auth", b -> b.nest(RequestPredicates.path("/**"),
                        nb -> nb.GET("/**", proxyTo("auth"))
                                .POST("/**", proxyTo("auth"))
                                .PUT("/**", proxyTo("auth"))
                                .PATCH("/**", proxyTo("auth"))
                                .DELETE("/**", proxyTo("auth"))))
                // Customer service
                .path("/api/v1/customers", b -> b.nest(RequestPredicates.path("/**"),
                        nb -> nb.GET("/**", proxyTo("customer"))
                                .POST("/**", proxyTo("customer"))
                                .PUT("/**", proxyTo("customer"))
                                .PATCH("/**", proxyTo("customer"))
                                .DELETE("/**", proxyTo("customer"))))
                // Account service
                .path("/api/v1/accounts", b -> b.nest(RequestPredicates.path("/**"),
                        nb -> nb.GET("/**", proxyTo("account"))
                                .POST("/**", proxyTo("account"))
                                .PUT("/**", proxyTo("account"))
                                .PATCH("/**", proxyTo("account"))
                                .DELETE("/**", proxyTo("account"))))
                // Transaction service
                .path("/api/v1/transactions", b -> b.nest(RequestPredicates.path("/**"),
                        nb -> nb.GET("/**", proxyTo("transaction"))
                                .POST("/**", proxyTo("transaction"))
                                .PUT("/**", proxyTo("transaction"))
                                .PATCH("/**", proxyTo("transaction"))
                                .DELETE("/**", proxyTo("transaction"))))
                // Beneficiary service
                .path("/api/v1/beneficiaries", b -> b.nest(RequestPredicates.path("/**"),
                        nb -> nb.GET("/**", proxyTo("beneficiary"))
                                .POST("/**", proxyTo("beneficiary"))
                                .PUT("/**", proxyTo("beneficiary"))
                                .PATCH("/**", proxyTo("beneficiary"))
                                .DELETE("/**", proxyTo("beneficiary"))))
                // UPI service
                .path("/api/v1/upi", b -> b.nest(RequestPredicates.path("/**"),
                        nb -> nb.GET("/**", proxyTo("upi"))
                                .POST("/**", proxyTo("upi"))
                                .PUT("/**", proxyTo("upi"))
                                .PATCH("/**", proxyTo("upi"))
                                .DELETE("/**", proxyTo("upi"))))
                // Statement service
                .path("/api/v1/statements", b -> b.nest(RequestPredicates.path("/**"),
                        nb -> nb.GET("/**", proxyTo("statement"))
                                .POST("/**", proxyTo("statement"))
                                .PUT("/**", proxyTo("statement"))
                                .PATCH("/**", proxyTo("statement"))
                                .DELETE("/**", proxyTo("statement"))))
                // Admin service
                .path("/api/v1/admin", b -> b.nest(RequestPredicates.path("/**"),
                        nb -> nb.GET("/**", proxyTo("admin"))
                                .POST("/**", proxyTo("admin"))
                                .PUT("/**", proxyTo("admin"))
                                .PATCH("/**", proxyTo("admin"))
                                .DELETE("/**", proxyTo("admin"))))
                // Fraud detection service
                .path("/api/v1/fraud", b -> b.nest(RequestPredicates.path("/**"),
                        nb -> nb.GET("/**", proxyTo("fraud"))
                                .POST("/**", proxyTo("fraud"))
                                .PUT("/**", proxyTo("fraud"))
                                .PATCH("/**", proxyTo("fraud"))
                                .DELETE("/**", proxyTo("fraud"))))
                // Audit service
                .path("/api/v1/audit-logs", b -> b.nest(RequestPredicates.path("/**"),
                        nb -> nb.GET("/**", proxyTo("audit"))
                                .POST("/**", proxyTo("audit"))
                                .PUT("/**", proxyTo("audit"))
                                .PATCH("/**", proxyTo("audit"))
                                .DELETE("/**", proxyTo("audit"))))
                .build();
    }

    private HandlerFunction<ServerResponse> proxyTo(String serviceKey) {
        return request -> {
            String baseUrl = gatewayProperties.getServices().get(serviceKey);
            if (baseUrl == null) {
                log.error("No service URL configured for key: {}", serviceKey);
                return ServerResponse.status(HttpStatus.BAD_GATEWAY)
                        .bodyValue("{\"error\":\"Service not configured\"}".getBytes());
            }

            String rawQuery = request.exchange().getRequest().getURI().getRawQuery();
            URI targetUri = UriComponentsBuilder.fromUri(URI.create(baseUrl))
                    .path(request.path())
                    .query(rawQuery)
                    .build(true)
                    .toUri();

            WebClient.RequestBodySpec requestSpec = webClient
                    .method(request.method())
                    .uri(targetUri)
                    .headers(h -> {
                        h.addAll(request.headers().asHttpHeaders());
                        h.remove(HttpHeaders.HOST);
                    });

            return request.bodyToMono(byte[].class)
                    .defaultIfEmpty(new byte[0])
                    .flatMap(body -> {
                        WebClient.ResponseSpec responseSpec = body.length > 0
                                ? requestSpec.bodyValue(body).retrieve()
                                : requestSpec.retrieve();
                        return responseSpec.toEntity(byte[].class);
                    })
                    .flatMap(entity -> {
                        ServerResponse.BodyBuilder builder =
                                ServerResponse.status(entity.getStatusCode());
                        if (entity.getHeaders() != null) {
                            entity.getHeaders().forEach((name, values) -> {
                                if (!HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(name)) {
                                    builder.headers(h -> h.addAll(name, values));
                                }
                            });
                        }
                        byte[] responseBody = entity.getBody();
                        return responseBody != null && responseBody.length > 0
                                ? builder.bodyValue(responseBody)
                                : builder.build();
                    })
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.warn("Downstream {} returned {}: {}",
                                serviceKey, ex.getStatusCode(), ex.getMessage());
                        byte[] body = ex.getResponseBodyAsByteArray();
                        return ServerResponse.status(ex.getStatusCode())
                                .bodyValue(body.length > 0 ? body
                                        : ("{\"error\":\"" + ex.getMessage() + "\"}").getBytes());
                    })
                    .onErrorResume(e -> {
                        log.error("Proxy error for {} → {}: {}", request.path(), serviceKey,
                                e.getMessage());
                        return ServerResponse.status(HttpStatus.BAD_GATEWAY)
                                .bodyValue("{\"error\":\"Service unavailable\"}".getBytes());
                    });
        };
    }
}
