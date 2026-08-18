package com.banking.upi.service;

import com.banking.upi.dto.request.UpiTransferRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class TransactionServiceClient {

    private final WebClient webClient;

    public TransactionServiceClient(WebClient.Builder builder,
                                    @Value("${transaction-service.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public UUID initiateTransfer(UUID fromAccountId, UUID toAccountId,
                                 UpiTransferRequest request, UUID initiatedBy,
                                 String idempotencyKey) {
        log.debug("Initiating UPI transfer: payer={}, payee={}, amount={}", request.getPayerVpa(), request.getPayeeVpa(), request.getAmount());

        var body = Map.of(
                "fromAccountId", fromAccountId.toString(),
                "toAccountId", toAccountId.toString(),
                "amount", request.getAmount(),
                "description", "UPI: " + request.getPayerVpa() + " → " + request.getPayeeVpa()
                        + (request.getRemarks() != null ? " | " + request.getRemarks() : ""),
                "idempotencyKey", idempotencyKey,
                "channel", "UPI"
        );

        var result = webClient.post()
                .uri("/api/v1/transactions/transfer")
                .header("X-User-Id", initiatedBy.toString())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (result == null || result.get("data") == null) {
            throw new IllegalStateException("Empty response from transaction service");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        return UUID.fromString(data.get("id").toString());
    }
}
