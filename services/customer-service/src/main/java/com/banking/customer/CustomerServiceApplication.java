package com.banking.customer;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.banking.customer", "com.banking.common"})
@ConfigurationPropertiesScan
@EnableScheduling
@OpenAPIDefinition(
        info = @Info(title = "Customer Service API", version = "v1",
                description = "Customer registration, KYC, and profile management"),
        servers = @Server(url = "http://localhost:8082")
)
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
