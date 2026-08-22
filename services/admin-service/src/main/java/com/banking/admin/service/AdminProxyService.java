package com.banking.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProxyService {

    @Qualifier("customerClient")
    private final WebClient customerClient;

    @Qualifier("accountClient")
    private final WebClient accountClient;

    @Qualifier("transactionClient")
    private final WebClient transactionClient;

    @Qualifier("fraudClient")
    private final WebClient fraudClient;

    // ── Customers ──────────────────────────────────────────────────────────

    public Mono<String> getCustomers(String page, String size, String search) {
        return customerClient.get()
                .uri(uri -> uri.path("/api/v1/customers")
                        .queryParamIfPresent("page", java.util.Optional.ofNullable(page))
                        .queryParamIfPresent("size", java.util.Optional.ofNullable(size))
                        .queryParamIfPresent("search", java.util.Optional.ofNullable(search))
                        .build())
                .header("X-User-Roles", "ROLE_ADMIN")
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new RuntimeException("Downstream error: " + body))))
                .bodyToMono(String.class)
                .onErrorReturn("{\"error\":\"Failed to fetch customers\"}");
    }

    public Mono<String> freezeCustomer(String customerId, String body) {
        return customerClient.post()
                .uri("/api/v1/customers/{id}/freeze", customerId)
                .header("X-User-Roles", "ROLE_ADMIN")
                .bodyValue(body != null ? body : "{}")
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new RuntimeException("Downstream error: " + b))))
                .bodyToMono(String.class)
                .onErrorReturn("{\"error\":\"Failed to freeze customer\"}");
    }

    public Mono<String> approveKyc(String customerId, String kycId) {
        return customerClient.post()
                .uri("/api/v1/customers/{id}/kyc/{kycId}/approve", customerId, kycId)
                .header("X-User-Roles", "ROLE_ADMIN")
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new RuntimeException("Downstream error: " + b))))
                .bodyToMono(String.class)
                .onErrorReturn("{\"error\":\"Failed to approve KYC\"}");
    }

    public Mono<String> rejectKyc(String customerId, String kycId, String body) {
        return customerClient.post()
                .uri("/api/v1/customers/{id}/kyc/{kycId}/reject", customerId, kycId)
                .header("X-User-Roles", "ROLE_ADMIN")
                .bodyValue(body != null ? body : "{}")
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new RuntimeException("Downstream error: " + b))))
                .bodyToMono(String.class)
                .onErrorReturn("{\"error\":\"Failed to reject KYC\"}");
    }

    // ── Accounts ───────────────────────────────────────────────────────────

    public Mono<String> getAccounts(String customerId, String page, String size) {
        return accountClient.get()
                .uri(uri -> uri.path("/api/v1/accounts")
                        .queryParamIfPresent("customerId", java.util.Optional.ofNullable(customerId))
                        .queryParamIfPresent("page", java.util.Optional.ofNullable(page))
                        .queryParamIfPresent("size", java.util.Optional.ofNullable(size))
                        .build())
                .header("X-User-Roles", "ROLE_ADMIN")
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new RuntimeException("Downstream error: " + b))))
                .bodyToMono(String.class)
                .onErrorReturn("{\"error\":\"Failed to fetch accounts\"}");
    }

    public Mono<String> freezeAccount(String accountId, String body) {
        return accountClient.post()
                .uri("/api/v1/accounts/{id}/freeze", accountId)
                .header("X-User-Roles", "ROLE_ADMIN")
                .bodyValue(body != null ? body : "{}")
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new RuntimeException("Downstream error: " + b))))
                .bodyToMono(String.class)
                .onErrorReturn("{\"error\":\"Failed to freeze account\"}");
    }

    // ── Transactions ───────────────────────────────────────────────────────

    public Mono<String> getTransactions(String accountId, String type, String status,
                                        String dateFrom, String dateTo, String page, String size) {
        return transactionClient.get()
                .uri(uri -> uri.path("/api/v1/transactions")
                        .queryParamIfPresent("accountId", java.util.Optional.ofNullable(accountId))
                        .queryParamIfPresent("type", java.util.Optional.ofNullable(type))
                        .queryParamIfPresent("status", java.util.Optional.ofNullable(status))
                        .queryParamIfPresent("dateFrom", java.util.Optional.ofNullable(dateFrom))
                        .queryParamIfPresent("dateTo", java.util.Optional.ofNullable(dateTo))
                        .queryParamIfPresent("page", java.util.Optional.ofNullable(page))
                        .queryParamIfPresent("size", java.util.Optional.ofNullable(size))
                        .build())
                .header("X-User-Roles", "ROLE_ADMIN")
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new RuntimeException("Downstream error: " + b))))
                .bodyToMono(String.class)
                .onErrorReturn("{\"error\":\"Failed to fetch transactions\"}");
    }

    public Mono<String> getTransaction(String transactionId) {
        return transactionClient.get()
                .uri("/api/v1/transactions/{id}", transactionId)
                .header("X-User-Roles", "ROLE_ADMIN")
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new RuntimeException("Downstream error: " + b))))
                .bodyToMono(String.class)
                .onErrorReturn("{\"error\":\"Failed to fetch transaction\"}");
    }

    // ── Fraud ──────────────────────────────────────────────────────────────

    public Mono<String> getFraudAlerts(String status, String page, String size) {
        return fraudClient.get()
                .uri(uri -> uri.path("/api/v1/fraud/alerts")
                        .queryParamIfPresent("status", java.util.Optional.ofNullable(status))
                        .queryParamIfPresent("page", java.util.Optional.ofNullable(page))
                        .queryParamIfPresent("size", java.util.Optional.ofNullable(size))
                        .build())
                .header("X-User-Roles", "ROLE_ADMIN")
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new RuntimeException("Downstream error: " + b))))
                .bodyToMono(String.class)
                .onErrorReturn("{\"error\":\"Failed to fetch fraud alerts\"}");
    }

    public Mono<String> resolveAlert(String alertId, String body) {
        return fraudClient.patch()
                .uri("/api/v1/fraud/alerts/{id}/resolve", alertId)
                .header("X-User-Roles", "ROLE_ADMIN")
                .bodyValue(body != null ? body : "{}")
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new RuntimeException("Downstream error: " + b))))
                .bodyToMono(String.class)
                .onErrorReturn("{\"error\":\"Failed to resolve alert\"}");
    }

    public Mono<String> blacklistAccount(String body) {
        return fraudClient.post()
                .uri("/api/v1/fraud/blacklist")
                .header("X-User-Roles", "ROLE_ADMIN")
                .bodyValue(body != null ? body : "{}")
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new RuntimeException("Downstream error: " + b))))
                .bodyToMono(String.class)
                .onErrorReturn("{\"error\":\"Failed to blacklist account\"}");
    }
}
