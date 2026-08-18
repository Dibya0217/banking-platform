package com.banking.account;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.banking.account", "com.banking.common"})
@ConfigurationPropertiesScan
@EnableScheduling
@OpenAPIDefinition(
        info = @Info(title = "Account Service API", version = "v1",
                description = "Account creation, balance management, and optimistic locking"),
        servers = @Server(url = "http://localhost:8083")
)
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
