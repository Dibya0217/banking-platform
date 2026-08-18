package com.banking.customer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class AuthServiceClient {

    private final WebClient webClient;
    private final String internalSecret;

    public AuthServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${internal.auth-service-url:http://localhost:8081}") String authServiceUrl,
            @Value("${internal.secret:internal-secret-change-me}") String internalSecret) {
        this.webClient = webClientBuilder.baseUrl(authServiceUrl).build();
        this.internalSecret = internalSecret;
    }

    public void createCredential(UUID customerId, String email, String mobile, String password) {
        Map<String, String> body = Map.of(
                "customerId", customerId.toString(),
                "email", email,
                "mobile", mobile,
                "password", password
        );
        webClient.post()
                .uri("/internal/credentials")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("X-Internal-Secret", internalSecret)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .doOnError(e -> log.error("Failed to create credential for customerId={}", customerId, e))
                .block();
    }
}
