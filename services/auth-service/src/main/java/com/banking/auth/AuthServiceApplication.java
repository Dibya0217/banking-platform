package com.banking.auth;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.banking.auth", "com.banking.common"})
@ConfigurationPropertiesScan
@OpenAPIDefinition(
        info = @Info(title = "Auth Service API", version = "v1",
                description = "JWT issuance, refresh, revocation, OTP, and credential management"),
        servers = @Server(url = "http://localhost:8081")
)
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
