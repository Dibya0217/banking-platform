package com.banking.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${services.customer}")
    private String customerUrl;

    @Value("${services.account}")
    private String accountUrl;

    @Value("${services.transaction}")
    private String transactionUrl;

    @Value("${services.fraud}")
    private String fraudUrl;

    @Bean("customerClient")
    public WebClient customerClient() {
        return WebClient.builder()
                .baseUrl(customerUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean("accountClient")
    public WebClient accountClient() {
        return WebClient.builder()
                .baseUrl(accountUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean("transactionClient")
    public WebClient transactionClient() {
        return WebClient.builder()
                .baseUrl(transactionUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean("fraudClient")
    public WebClient fraudClient() {
        return WebClient.builder()
                .baseUrl(fraudUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
