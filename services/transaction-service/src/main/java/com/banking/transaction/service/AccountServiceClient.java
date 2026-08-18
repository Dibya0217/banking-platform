package com.banking.transaction.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AccountServiceClient {

    private final WebClient webClient;
    private final String internalSecret;

    public AccountServiceClient(WebClient.Builder webClientBuilder,
                                @Value("${account-service.base-url}") String baseUrl,
                                @Value("${internal.secret}") String internalSecret) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.internalSecret = internalSecret;
    }

    public void debit(UUID accountId, BigDecimal amount, String transactionId) {
        log.debug("Debiting account={}, amount={}, txn={}", accountId, amount, transactionId);
        webClient.post()
                .uri("/internal/accounts/{id}/debit", accountId)
                .header("X-Internal-Secret", internalSecret)
                .bodyValue(Map.of("amount", amount, "transactionId", transactionId))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    public void credit(UUID accountId, BigDecimal amount, String transactionId) {
        log.debug("Crediting account={}, amount={}, txn={}", accountId, amount, transactionId);
        webClient.post()
                .uri("/internal/accounts/{id}/credit", accountId)
                .header("X-Internal-Secret", internalSecret)
                .bodyValue(Map.of("amount", amount, "transactionId", transactionId))
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
